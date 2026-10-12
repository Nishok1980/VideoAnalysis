package in.nishok.videoanalysis.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import in.nishok.videoanalysis.domain.Model.VideoInfo;
import java.util.UUID;

/** Local-file decoder. Commands are argument lists, never shell strings. */
public final class FfmpegVideo
{
    public static final int SIZE = 640;
    public static final int SAMPLE_FPS = 2;
    private final String ffmpeg;
    private final String ffprobe;

    public FfmpegVideo(String ffmpeg, String ffprobe)
    {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    public void check() throws Exception
    {
        run(List.of(ffmpeg, "-version"), Duration.ofSeconds(10), () -> false);
        run(List.of(ffprobe, "-version"), Duration.ofSeconds(10), () -> false);
    }

    public VideoInfo probe(Path source, UUID id, String name) throws Exception
    {
        String output = run(List.of(ffprobe, "-v", "error",
            "-protocol_whitelist", "file,pipe", "-select_streams", "v:0",
            "-show_entries", "stream=width,height:format=duration",
            "-of", "default=noprint_wrappers=1", source.toString()),
            Duration.ofSeconds(30), () -> false);
        int width = 0;
        int height = 0;
        double duration = 0;
        for (String line : output.split("\\R"))
        {
            String[] pair = line.split("=", 2);
            if (pair.length != 2) { continue; }
            switch (pair[0])
            {
                case "width" -> width = Integer.parseInt(pair[1]);
                case "height" -> height = Integer.parseInt(pair[1]);
                case "duration" -> duration = Double.parseDouble(pair[1]);
                default -> { }
            }
        }
        if (width < 2 || height < 2 || width > 4096 || height > 4096
            || !Double.isFinite(duration) || duration <= 0 || duration > 1800)
        {
            throw new IllegalArgumentException(
                "Use a video up to 30 minutes and 4096 pixels per dimension.");
        }
        return new VideoInfo(id, name, duration, width, height);
    }

    public void thumbnail(Path source, Path target) throws Exception
    {
        var args = input(source);
        args.addAll(List.of("-frames:v", "1", "-vf", "scale=960:-1",
            "-threads", "1", "-update", "1", target.toString()));
        run(args, Duration.ofSeconds(60), () -> false);
    }

    public void preview(Path source, Path target, BooleanSupplier cancelled)
        throws Exception
    {
        var args = input(source);
        args.addAll(List.of("-map", "0:v:0", "-an", "-vf",
            "setpts=PTS-STARTPTS,scale=1280:720:force_original_aspect_ratio="
            + "decrease:force_divisible_by=2,setsar=1",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "25",
            "-threads", "2", "-pix_fmt", "yuv420p", "-movflags",
            "+faststart", target.toString()));
        run(args, Duration.ofMinutes(30), cancelled);
    }

    @FunctionalInterface
    public interface SampleConsumer
    {
        void accept(double seconds, byte[] rgb) throws Exception;
    }

    /** FPS filter uses a zero-based timeline matching the preview. */
    public void samples(Path source, BooleanSupplier cancelled,
        SampleConsumer consumer) throws Exception
    {
        var args = input(source);
        args.addAll(List.of("-map", "0:v:0", "-an", "-vf",
            "setpts=PTS-STARTPTS,fps=2:start_time=0,"
            + "scale=640:640:force_original_aspect_ratio=decrease,"
            + "pad=640:640:(ow-iw)/2:(oh-ih)/2:color=0x727272,setsar=1",
            "-threads", "1", "-pix_fmt", "rgb24", "-f", "rawvideo",
            "pipe:1"));
        Path errors = Files.createTempFile("va-decoder-", ".log");
        Process process = null;
        Thread watchdog = null;
        try
        {
            process = new ProcessBuilder(args)
                .redirectError(errors.toFile()).start();
            final Process child = process;
            watchdog = new Thread(() ->
            {
                long deadline = System.nanoTime()
                    + Duration.ofMinutes(60).toNanos();
                try
                {
                    while (child.isAlive())
                    {
                        if (cancelled.getAsBoolean()
                            || System.nanoTime() > deadline)
                        {
                            child.destroyForcibly();
                            return;
                        }
                        Thread.sleep(100);
                    }
                }
                catch (InterruptedException ignored)
                {
                    Thread.currentThread().interrupt();
                }
            }, "decoder-watchdog");
            watchdog.setDaemon(true);
            watchdog.start();
            try (var stream = process.getInputStream())
            {
                long frame = 0;
                while (true)
                {
                    if (cancelled.getAsBoolean())
                    {
                        throw new CancellationException();
                    }
                    byte[] rgb = stream.readNBytes(SIZE * SIZE * 3);
                    if (rgb.length == 0) { break; }
                    if (rgb.length != SIZE * SIZE * 3)
                    {
                        throw new IOException("Truncated decoded frame");
                    }
                    consumer.accept(frame++ / (double) SAMPLE_FPS, rgb);
                }
                if (frame == 0) { throw new IOException("No video frames"); }
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)
                || process.exitValue() != 0)
            {
                throw new IOException("Video decoding failed");
            }
        }
        finally
        {
            if (process != null) { process.destroyForcibly(); }
            if (watchdog != null) { watchdog.interrupt(); }
            Files.deleteIfExists(errors);
        }
    }

    private ArrayList<String> input(Path source)
    {
        return new ArrayList<>(List.of(ffmpeg, "-nostdin", "-v", "error",
            "-y", "-protocol_whitelist", "file,pipe", "-noautorotate",
            "-i", source.toString()));
    }

    private String run(List<String> args, Duration timeout,
        BooleanSupplier cancelled) throws Exception
    {
        Path output = Files.createTempFile("va-process-", ".log");
        Process process = null;
        try
        {
            process = new ProcessBuilder(args).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
            long deadline = System.nanoTime() + timeout.toNanos();
            while (!process.waitFor(100, TimeUnit.MILLISECONDS))
            {
                if (cancelled.getAsBoolean())
                {
                    throw new CancellationException();
                }
                if (System.nanoTime() > deadline)
                {
                    throw new IOException("Video operation timed out");
                }
            }
            if (process.exitValue() != 0)
            {
                throw new IOException(
                    "FFmpeg/FFprobe could not read or convert the video. "
                    + "Export an MP4/H.264 recording and check your tools.");
            }
            try (var stream = Files.newInputStream(output))
            {
                return new String(stream.readNBytes(65536),
                    java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        finally
        {
            if (process != null) { process.destroyForcibly(); }
            Files.deleteIfExists(output);
        }
    }
}

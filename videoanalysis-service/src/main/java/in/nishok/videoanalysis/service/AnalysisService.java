package in.nishok.videoanalysis.service;

import static in.nishok.videoanalysis.domain.Model.*;
import in.nishok.videoanalysis.domain.RuleEngine;
import in.nishok.videoanalysis.detection.Detector;
import in.nishok.videoanalysis.video.FfmpegVideo;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded, single-worker local sessions. No media bytes are sent to a service.
 * One active job avoids competing decoder/model memory on a home computer.
 */
public final class AnalysisService implements AutoCloseable
{
    @FunctionalInterface
    public interface DetectorFactory { Detector create() throws Exception; }

    private final FfmpegVideo video;
    private final DetectorFactory factory;
    private final Path session;
    private final Map<UUID, VideoInfo> videos = new ConcurrentHashMap<>();
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean busy;
    private boolean closing;

    public AnalysisService(FfmpegVideo video, DetectorFactory factory,
        Path dataRoot) throws IOException
    {
        this.video = video;
        this.factory = factory;
        Files.createDirectories(dataRoot);
        session = Files.createTempDirectory(dataRoot.toAbsolutePath(),
            "session-");
    }

    public synchronized VideoInfo upload(InputStream input, String filename)
        throws Exception
    {
        if (closing) { throw new IllegalStateException("App is closing"); }
        if (videos.size() >= 5)
        {
            throw new IllegalStateException(
                "Five video limit reached. Delete a video before uploading.");
        }
        String name = filename == null ? "video.mp4"
            : filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (!name.toLowerCase(java.util.Locale.ROOT)
            .matches(".*\\.(mp4|mkv|avi|mov)"))
        {
            throw new IllegalArgumentException("Use MP4, MKV, AVI or MOV");
        }
        if (name.length() > 200) { name = name.substring(name.length() - 200); }
        UUID id = UUID.randomUUID();
        Path folder = session.resolve(id.toString());
        Files.createDirectory(folder);
        try
        {
            Path source = folder.resolve("source.video");
            try (var output = Files.newOutputStream(source))
            {
                byte[] buffer = new byte[65536];
                long total = 0;
                int count;
                while ((count = input.read(buffer)) != -1)
                {
                    total += count;
                    if (total > 250L * 1024 * 1024)
                    {
                        throw new IllegalArgumentException("250 MB upload limit");
                    }
                    output.write(buffer, 0, count);
                }
            }
            VideoInfo info = video.probe(source, id, name);
            video.thumbnail(source, folder.resolve("thumbnail.jpg"));
            videos.put(id, info);
            return info;
        }
        catch (Exception failure)
        {
            deleteTree(folder);
            throw failure;
        }
    }

    public List<VideoInfo> videos() { return List.copyOf(videos.values()); }

    public synchronized JobView start(UUID videoId, Rules rules)
    {
        if (busy || closing)
        {
            throw new IllegalStateException("Wait for the current analysis");
        }
        if (jobs.size() >= 20)
        {
            throw new IllegalStateException("Delete old videos to free jobs");
        }
        requireVideo(videoId);
        Job job = new Job(UUID.randomUUID(), videoId, rules);
        jobs.put(job.id, job);
        busy = true;
        worker.submit(() -> execute(job));
        return job.view();
    }

    private void execute(Job job)
    {
        try (Detector detector = factory.create())
        {
            job.modelSha256 = detector.fingerprint();
            if (job.cancel.get()) { throw new CancellationException(); }
            job.state = "RUNNING";
            job.stage = "Preparing browser playback";
            Path folder = folder(job.videoId);
            Path preview = folder.resolve("preview.mp4");
            if (!Files.isRegularFile(preview))
            {
                Path partial = folder.resolve("preview.partial.mp4");
                try
                {
                    video.preview(folder.resolve("source.video"), partial,
                        job.cancel::get);
                    Files.move(partial, preview);
                }
                finally { Files.deleteIfExists(partial); }
            }
            job.previewReady = true;
            job.stage = "Analysing sampled frames";
            VideoInfo info = requireVideo(job.videoId);
            video.samples(folder.resolve("source.video"), job.cancel::get,
                (seconds, rgb) ->
                {
                    if (job.cancel.get()) { throw new CancellationException(); }
                    job.engine.accept(seconds, detector.detect(rgb,
                        info.width(), info.height(), job.rules.confidence()));
                    job.progress = Math.min(99, 100 * seconds
                        / info.durationSeconds());
                });
            if (job.cancel.get()) { throw new CancellationException(); }
            job.progress = 100;
            job.stage = "Review findings";
            job.state = "COMPLETED";
        }
        catch (Exception | LinkageError failure)
        {
            if (job.cancel.get() || failure instanceof CancellationException)
            {
                job.stage = "Stopped; any findings are partial";
                job.state = "CANCELLED";
            }
            else
            {
                job.error = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
                job.stage = "Analysis failed; any findings are partial";
                job.state = "FAILED";
                System.getLogger(AnalysisService.class.getName()).log(
                    System.Logger.Level.WARNING,
                    "Analysis job " + job.id + " failed", failure);
            }
        }
        finally
        {
            synchronized (this) { busy = false; }
        }
    }

    public List<JobView> jobs()
    {
        return jobs.values().stream().map(Job::view).toList();
    }

    public JobView job(UUID id) { return requireJob(id).view(); }

    public void cancel(UUID id)
    {
        Job job = requireJob(id);
        if (job.state.equals("RUNNING") || job.state.equals("QUEUED"))
        {
            job.cancel.set(true);
            job.stage = "Cancellation requested";
        }
    }

    public void review(UUID jobId, UUID eventId, Review review)
    {
        if (review == null) { throw new IllegalArgumentException("Review required"); }
        if (!requireJob(jobId).engine.review(eventId, review))
        {
            throw new NoSuchElementException("Event not found");
        }
    }

    public Path asset(UUID videoId, String name)
    {
        requireVideo(videoId);
        if (!name.equals("thumbnail.jpg") && !name.equals("preview.mp4"))
        {
            throw new NoSuchElementException("Asset not found");
        }
        Path path = folder(videoId).resolve(name);
        if (!Files.isRegularFile(path))
        {
            throw new NoSuchElementException("Preview is not ready yet");
        }
        return path;
    }

    public synchronized void delete(UUID videoId) throws IOException
    {
        requireVideo(videoId);
        // No deletion while native decoding may hold a file on Windows.
        if (busy)
        {
            throw new IllegalStateException("Wait for analysis to finish");
        }
        deleteTree(folder(videoId));
        videos.remove(videoId);
        jobs.values().removeIf(job -> job.videoId.equals(videoId));
    }

    private VideoInfo requireVideo(UUID id)
    {
        VideoInfo info = videos.get(id);
        if (info == null) { throw new NoSuchElementException("Video not found"); }
        return info;
    }

    private Job requireJob(UUID id)
    {
        Job job = jobs.get(id);
        if (job == null) { throw new NoSuchElementException("Job not found"); }
        return job;
    }

    private Path folder(UUID id) { return session.resolve(id.toString()); }

    private void deleteTree(Path path) throws IOException
    {
        if (!Files.exists(path)) { return; }
        try (var paths = Files.walk(path))
        {
            for (var item : paths.sorted(Comparator.reverseOrder()).toList())
            {
                Files.deleteIfExists(item);
            }
        }
    }

    @Override
    public void close() throws Exception
    {
        synchronized (this) { closing = true; }
        jobs.values().forEach(job -> job.cancel.set(true));
        worker.shutdown();
        if (worker.awaitTermination(30, TimeUnit.SECONDS))
        {
            deleteTree(session);
        }
        else { worker.shutdownNow(); }
    }

    private static final class Job
    {
        final UUID id;
        final UUID videoId;
        final Rules rules;
        final RuleEngine engine;
        final AtomicBoolean cancel = new AtomicBoolean();
        volatile String state = "QUEUED";
        volatile String stage = "Opening detection model";
        volatile double progress;
        volatile String error;
        volatile String modelSha256;
        volatile boolean previewReady;

        Job(UUID id, UUID videoId, Rules rules)
        {
            this.id = id;
            this.videoId = videoId;
            this.rules = rules;
            engine = new RuleEngine(rules);
        }

        JobView view()
        {
            return new JobView(id, videoId, state, stage, progress,
                error, rules, engine.events(), previewReady, modelSha256);
        }
    }
}

package in.nishok.videoanalysis.video;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

class FfmpegVideoTest
{
    @TempDir Path temporary;

    @Test void realDecoderPreviewAndCancellation() throws Exception
    {
        String fixture = System.getenv("VIDEO_TEST_CLIP");
        Assumptions.assumeTrue(fixture != null, "Set VIDEO_TEST_CLIP");
        var decoder = new FfmpegVideo("ffmpeg", "ffprobe");
        Path source = Path.of(fixture);
        var info = decoder.probe(source, UUID.randomUUID(), "fixture.mp4");
        assertTrue(info.durationSeconds() > 1);
        Path thumbnail = temporary.resolve("thumbnail.jpg");
        Path preview = temporary.resolve("preview.mp4");
        decoder.thumbnail(source, thumbnail);
        decoder.preview(source, preview, () -> false);
        assertTrue(Files.size(thumbnail) > 0);
        assertTrue(Files.size(preview) > 0);
        var positions = new ArrayList<Double>();
        decoder.samples(source, () -> false, (seconds, bytes) ->
        {
            assertEquals(640 * 640 * 3, bytes.length);
            positions.add(seconds);
        });
        assertEquals(0, positions.get(0));
        assertEquals(.5, positions.get(1));
        var cancel = new AtomicBoolean();
        assertThrows(CancellationException.class, () -> decoder.samples(
            source, cancel::get, (seconds, bytes) -> cancel.set(true)));
    }
}

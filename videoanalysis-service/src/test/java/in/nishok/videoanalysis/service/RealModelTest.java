package in.nishok.videoanalysis.service;

import static org.junit.jupiter.api.Assertions.*;
import in.nishok.videoanalysis.detection.YoloV10Detector;
import in.nishok.videoanalysis.video.FfmpegVideo;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class RealModelTest
{
    @Test void realModelFindsPeopleInReferenceImage() throws Exception
    {
        String model = System.getenv("VIDEO_TEST_MODEL");
        String clip = System.getenv("VIDEO_PEOPLE_CLIP");
        Assumptions.assumeTrue(model != null && clip != null,
            "Set VIDEO_TEST_MODEL and VIDEO_PEOPLE_CLIP");
        try (var detector = new YoloV10Detector(Path.of(model)))
        {
            var count = new AtomicInteger();
            var video = new FfmpegVideo("ffmpeg", "ffprobe");
            var info = video.probe(Path.of(clip), java.util.UUID.randomUUID(),
                "people.mp4");
            video.samples(Path.of(clip), () -> false, (seconds, rgb) ->
            {
                var detections = detector.detect(rgb, info.width(),
                    info.height(), .45);
                count.addAndGet((int) detections.stream()
                    .filter(d -> d.label().equals("person")).count());
            });
            assertTrue(count.get() > 0, "Reference people must be detected");
            assertTrue(detector.detect(new byte[640 * 640 * 3],
                640, 640, .45).isEmpty(), "Black frame should be empty");
        }
    }
}

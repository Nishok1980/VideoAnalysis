package in.nishok.videoanalysis.detection;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class YoloOutputTest
{
    @Test void wideImageLetterboxIsRemoved()
    {
        float[][] rows = {{64, 176, 192, 284, .9f, 0},
            {0, 0, 10, 10, .9f, 16}, {64, 176, 192, 284, .9f, 2}};
        var detections = YoloOutput.decode(rows, 1920, 1080, .45);
        assertEquals(1, detections.size());
        var box = detections.get(0).box();
        assertEquals(.1, box.x(), .001);
        assertEquals(.1, box.y(), .001);
        assertEquals(.2, box.width(), .001);
        assertEquals(.3, box.height(), .001);
    }

    @Test void nonFiniteOutputIsIgnored()
    {
        assertTrue(YoloOutput.decode(new float[][]{
            {Float.NaN, 1, 10, 10, .9f, 0}}, 640, 640, .45).isEmpty());
    }
}

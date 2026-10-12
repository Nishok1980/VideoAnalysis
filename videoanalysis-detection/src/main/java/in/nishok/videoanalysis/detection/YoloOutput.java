package in.nishok.videoanalysis.detection;

import java.util.ArrayList;
import java.util.List;
import in.nishok.videoanalysis.domain.Model.Box;
import in.nishok.videoanalysis.domain.Model.Detection;

/** Maps letterboxed pixel xyxy coordinates back to original fractions. */
public final class YoloOutput
{
    private YoloOutput() { }

    public static List<Detection> decode(float[][] rows, int width,
        int height, double threshold)
    {
        double ratio = Math.min(640.0 / width, 640.0 / height);
        // FFmpeg rounds the scaled dimensions to integer pixels.
        int scaledWidth = (int) Math.round(width * ratio);
        int scaledHeight = (int) Math.round(height * ratio);
        int left = (640 - scaledWidth) / 2;
        int top = (640 - scaledHeight) / 2;
        var detections = new ArrayList<Detection>();
        for (float[] row : rows)
        {
            if (row.length != 6)
            {
                throw new IllegalArgumentException("Invalid output shape");
            }
            boolean finite = true;
            for (float value : row) { finite &= Float.isFinite(value); }
            if (!finite || row[4] < threshold || row[4] > 1) { continue; }
            int label = Math.round(row[5]);
            if (label != 0 && label != 16) { continue; }
            double x1 = clamp((row[0] - left) / scaledWidth);
            double y1 = clamp((row[1] - top) / scaledHeight);
            double x2 = clamp((row[2] - left) / scaledWidth);
            double y2 = clamp((row[3] - top) / scaledHeight);
            if (x2 <= x1 || y2 <= y1) { continue; }
            detections.add(new Detection(label == 0 ? "person" : "dog",
                row[4], new Box(x1, y1, x2 - x1, y2 - y1)));
        }
        return List.copyOf(detections);
    }

    private static double clamp(double value)
    {
        return Math.max(0, Math.min(1, value));
    }
}

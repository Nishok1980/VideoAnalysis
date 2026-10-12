package in.nishok.videoanalysis.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable API values. Coordinates are fractions of the original image. */
public final class Model
{
    private Model() { }

    public record Box(double x, double y, double width, double height)
    {
        public Box
        {
            if (!Double.isFinite(x + y + width + height)
                || x < 0 || y < 0 || width <= 0 || height <= 0
                || x + width > 1.00001 || y + height > 1.00001)
            {
                throw new IllegalArgumentException("Invalid rectangle");
            }
        }

        public double centerX() { return x + width / 2; }
        public double centerY() { return y + height / 2; }

        public boolean containsFoot(Box other)
        {
            double px = other.centerX();
            double py = other.y + other.height;
            return px >= x && px <= x + width
                && py >= y && py <= y + height;
        }

        public double iou(Box other)
        {
            double w = Math.max(0, Math.min(x + width,
                other.x + other.width) - Math.max(x, other.x));
            double h = Math.max(0, Math.min(y + height,
                other.y + other.height) - Math.max(y, other.y));
            double intersection = w * h;
            return intersection / (width * height
                + other.width * other.height - intersection);
        }
    }

    public record Detection(String label, double confidence, Box box) { }

    public record Rules(String areaName, Box area,
        OffsetDateTime recordingStart, LocalTime fromTime, LocalTime toTime,
        Set<DayOfWeek> days, double presenceSeconds, double lingerSeconds,
        int entryCount, double entryWindowSeconds,
        double interactionSeconds, double proximity, double confidence)
    {
        public Rules
        {
            if (areaName == null || areaName.isBlank()
                || areaName.length() > 80 || area == null)
            {
                throw new IllegalArgumentException("Name and area required");
            }
            days = days == null ? Set.of() : Set.copyOf(days);
            if ((fromTime == null) != (toTime == null))
            {
                throw new IllegalArgumentException("Provide both times");
            }
            if ((fromTime != null || !days.isEmpty())
                && recordingStart == null)
            {
                throw new IllegalArgumentException(
                    "Recording start required for schedule rules");
            }
            if (!Double.isFinite(presenceSeconds + lingerSeconds
                + entryWindowSeconds + interactionSeconds
                + proximity + confidence)
                || presenceSeconds < 1 || presenceSeconds > 600
                || lingerSeconds < presenceSeconds || lingerSeconds > 1800
                || entryCount < 2 || entryCount > 100
                || entryWindowSeconds < 5 || entryWindowSeconds > 1800
                || interactionSeconds < 1 || interactionSeconds > 600
                || proximity < 0.01 || proximity > 1
                || confidence < 0.1 || confidence > 1)
            {
                throw new IllegalArgumentException("Rule values out of range");
            }
        }

        /** Overnight windows belong to the day on which they start. */
        public boolean active(double seconds)
        {
            if (recordingStart == null) { return true; }
            var moment = recordingStart.plusNanos((long) (seconds * 1e9));
            var time = moment.toLocalTime();
            var day = moment.getDayOfWeek();
            if (fromTime != null && !fromTime.equals(toTime))
            {
                boolean overnight = fromTime.isAfter(toTime);
                boolean inside = overnight
                    ? !time.isBefore(fromTime) || time.isBefore(toTime)
                    : !time.isBefore(fromTime) && time.isBefore(toTime);
                if (!inside) { return false; }
                if (overnight && time.isBefore(toTime))
                {
                    day = moment.minusDays(1).getDayOfWeek();
                }
            }
            return days.isEmpty() || days.contains(day);
        }
    }

    public enum Review { UNREVIEWED, RELEVANT, FALSE_ALERT, UNCERTAIN }

    public record Event(UUID id, String type, long trackId,
        double startSeconds, double endSeconds, String observedAt,
        String reason, double detectionConfidence, Box box, Review review)
    {
        public Event reviewed(Review value)
        {
            return new Event(id, type, trackId, startSeconds, endSeconds,
                observedAt, reason, detectionConfidence, box, value);
        }
    }

    public record VideoInfo(UUID id, String filename, double durationSeconds,
        int width, int height) { }

    public record JobView(UUID id, UUID videoId, String state, String stage,
        double progress, String error, Rules rules, List<Event> events,
        boolean previewReady, String modelSha256) { }
}

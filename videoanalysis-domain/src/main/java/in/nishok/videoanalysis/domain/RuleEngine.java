package in.nishok.videoanalysis.domain;

import static in.nishok.videoanalysis.domain.Model.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-job short-term IoU tracking and explainable rules, not identity matching.
 * No track is linked across a gap longer than 1.5 seconds. Rule durations
 * refer to sampled observations and may include shorter detection gaps.
 */
public final class RuleEngine
{
    private final Rules rules;
    private final Map<Long, Track> tracks = new HashMap<>();
    private final Map<String, Event> events = new LinkedHashMap<>();
    private final ArrayDeque<Double> entries = new ArrayDeque<>();
    private long nextId;
    private double lastTime = -1;
    private double lastRepeated = -Double.MAX_VALUE;

    public RuleEngine(Rules rules) { this.rules = rules; }

    public synchronized void accept(double time, List<Detection> detections)
    {
        if (!Double.isFinite(time) || time <= lastTime)
        {
            throw new IllegalArgumentException("Frame times must increase");
        }
        lastTime = time;
        tracks.values().removeIf(t -> time - t.lastSeen > 1.5);
        if (!rules.active(time))
        {
            tracks.clear();
            entries.clear();
            return;
        }
        var used = new HashSet<Long>();
        var current = new ArrayList<Track>();
        for (var d : detections.stream()
            .filter(d -> d.confidence() >= rules.confidence())
            .sorted(Comparator.comparingDouble(Detection::confidence)
                .reversed()).toList())
        {
            Track best = null;
            double score = 0.15;
            for (var t : tracks.values())
            {
                double candidate = t.box.iou(d.box());
                if (!used.contains(t.id) && t.label.equals(d.label())
                    && candidate > score)
                {
                    best = t;
                    score = candidate;
                }
            }
            if (best == null)
            {
                best = new Track(++nextId, d);
                tracks.put(best.id, best);
            }
            best.box = d.box();
            best.confidence = d.confidence();
            best.lastSeen = time;
            used.add(best.id);
            current.add(best);
        }
        // Require observed proximity each sample; missing pairs reset it.
        for (var t : tracks.values())
        {
            if (!used.contains(t.id)) { t.nearSince = -1; }
        }
        for (var person : current)
        {
            if (!person.label.equals("person")) { continue; }
            if (!rules.area().containsFoot(person.box))
            {
                person.insideSince = -1;
                person.nearSince = -1;
                continue;
            }
            if (person.insideSince < 0)
            {
                person.insideSince = time;
                person.episode++;
                entries.addLast(time);
                while (!entries.isEmpty()
                    && time - entries.peekFirst() > rules.entryWindowSeconds())
                {
                    entries.removeFirst();
                }
                if (entries.size() >= rules.entryCount()
                    && time - lastRepeated >= rules.entryWindowSeconds())
                {
                    emit("REPEATED_ENTRIES", person, entries.peekFirst(), time,
                        "Repeated entries in " + rules.areaName()
                        + "; may be different people or fragmented tracks.");
                    lastRepeated = time;
                }
            }
            double duration = time - person.insideSince;
            if (duration >= rules.presenceSeconds())
            {
                emit("PERSON_PRESENT", person, person.insideSince, time,
                    "Person observed in " + rules.areaName()
                    + " during the monitoring window.");
            }
            if (duration >= rules.lingerSeconds())
            {
                emit("LINGERING", person, person.insideSince, time,
                    "Person remained in " + rules.areaName()
                    + " beyond " + rules.lingerSeconds() + " seconds.");
            }
            boolean nearbyDog = current.stream().anyMatch(dog ->
                dog.label.equals("dog")
                && rules.area().containsFoot(dog.box)
                && distance(person.box, dog.box) <= rules.proximity());
            if (nearbyDog)
            {
                if (person.nearSince < 0)
                {
                    person.nearSince = time;
                    person.nearEpisode++;
                }
                if (time - person.nearSince >= rules.interactionSeconds())
                {
                    emit("PERSON_DOG_PROXIMITY", person, person.nearSince,
                        time, "Person and dog close in the image in "
                        + rules.areaName()
                        + ". Possible interaction; feeding is not established.");
                }
            }
            else { person.nearSince = -1; }
        }
    }

    private double distance(Box a, Box b)
    {
        double dx = Math.max(0, Math.max(a.x() - b.x() - b.width(),
            b.x() - a.x() - a.width()));
        double dy = Math.max(0, Math.max(a.y() - b.y() - b.height(),
            b.y() - a.y() - a.height()));
        return Math.hypot(dx, dy);
    }

    private void emit(String type, Track t, double start, double end,
        String reason)
    {
        String key = type + ":" + t.id + ":" + t.episode
            + (type.equals("PERSON_DOG_PROXIMITY") ? ":" + t.nearEpisode : "");
        Event prior = events.get(key);
        if (prior == null && events.size() >= 2000)
        {
            throw new IllegalStateException(
                "Event limit reached. Use a shorter recording or tighter rules.");
        }
        String observedAt = rules.recordingStart() == null ? null
            : rules.recordingStart().plusNanos((long) (start * 1e9)).toString();
        events.put(key, new Event(prior == null ? UUID.randomUUID() : prior.id(),
            type, t.id, start, end, observedAt, reason, t.confidence, t.box,
            prior == null ? Review.UNREVIEWED : prior.review()));
    }

    public synchronized List<Event> events()
    {
        return List.copyOf(events.values());
    }

    public synchronized boolean review(UUID id, Review review)
    {
        for (var entry : events.entrySet())
        {
            if (entry.getValue().id().equals(id))
            {
                entry.setValue(entry.getValue().reviewed(review));
                return true;
            }
        }
        return false;
    }

    private static final class Track
    {
        final long id;
        final String label;
        Box box;
        double confidence;
        double lastSeen;
        double insideSince = -1;
        double nearSince = -1;
        int episode;
        int nearEpisode;

        Track(long id, Detection detection)
        {
            this.id = id;
            label = detection.label();
            box = detection.box();
        }
    }
}

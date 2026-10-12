package in.nishok.videoanalysis.domain;

import static in.nishok.videoanalysis.domain.Model.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleEngineTest
{
    private Rules rules()
    {
        return new Rules("Gate", new Box(0, 0, 1, 1), null, null,
            null, Set.of(), 1, 3, 3, 10, 2, .08, .45);
    }

    private Detection person()
    {
        return new Detection("person", .9, new Box(.2, .2, .2, .4));
    }

    @Test void durationAndReviewSurviveUpdates()
    {
        var engine = new RuleEngine(rules());
        for (int i = 0; i <= 6; i++)
        {
            engine.accept(i * .5, List.of(person()));
        }
        assertEquals(2, engine.events().size());
        var linger = engine.events().stream()
            .filter(e -> e.type().equals("LINGERING")).findFirst().orElseThrow();
        assertEquals(0, linger.startSeconds());
        assertEquals(3, linger.endSeconds());
        assertTrue(engine.review(linger.id(), Review.FALSE_ALERT));
        engine.accept(3.5, List.of(person()));
        var updated = engine.events().stream()
            .filter(e -> e.id().equals(linger.id())).findFirst().orElseThrow();
        assertEquals(Review.FALSE_ALERT, updated.review());
        assertEquals(3.5, updated.endSeconds());
    }

    @Test void missingTrackDoesNotAccumulateLongLinger()
    {
        var engine = new RuleEngine(rules());
        engine.accept(0, List.of(person()));
        engine.accept(1, List.of(person()));
        engine.accept(5, List.of(person()));
        engine.accept(6, List.of(person()));
        assertTrue(engine.events().stream()
            .noneMatch(e -> e.type().equals("LINGERING")));
    }

    @Test void dogAloneOrDistantDogDoesNotTriggerInteraction()
    {
        var engine = new RuleEngine(rules());
        var farDog = new Detection("dog", .95, new Box(.85, .85, .1, .1));
        for (int i = 0; i <= 8; i++)
        {
            engine.accept(i * .5, List.of(person(), farDog));
        }
        assertTrue(engine.events().stream()
            .noneMatch(e -> e.type().equals("PERSON_DOG_PROXIMITY")));
    }

    @Test void nearbyDogIsOnlyAProximityEvent()
    {
        var engine = new RuleEngine(rules());
        var dog = new Detection("dog", .9, new Box(.42, .4, .15, .2));
        for (int i = 0; i <= 4; i++)
        {
            engine.accept(i * .5, List.of(person(), dog));
        }
        var event = engine.events().stream()
            .filter(e -> e.type().equals("PERSON_DOG_PROXIMITY"))
            .findFirst().orElseThrow();
        assertTrue(event.reason().contains("feeding is not established"));
    }

    @Test void oneDetectionCannotUpdateTwoTracks()
    {
        var engine = new RuleEngine(rules());
        var second = new Detection("person", .8, new Box(.32, .2, .2, .4));
        engine.accept(0, List.of(person(), second));
        engine.accept(.5, List.of(person()));
        engine.accept(1, List.of(person()));
        assertEquals(1, engine.events().size());
    }

    @Test void repeatedEntryCountDoesNotClaimIdentity()
    {
        var engine = new RuleEngine(rules());
        engine.accept(0, List.of(person()));
        engine.accept(3, List.of(person()));
        engine.accept(6, List.of(person()));
        assertTrue(engine.events().stream().anyMatch(e ->
            e.type().equals("REPEATED_ENTRIES")
                && e.reason().contains("different people")));
    }

    @Test void overnightWindowBelongsToPreviousDay()
    {
        var base = rules();
        var scheduled = new Rules("Gate", base.area(),
            OffsetDateTime.parse("2026-10-12T23:00:00+05:30"),
            LocalTime.of(23, 0), LocalTime.of(5, 0), Set.of(DayOfWeek.MONDAY),
            1, 3, 3, 10, 2, .08, .45);
        assertTrue(scheduled.active(0));
        assertTrue(scheduled.active(7200));
        assertFalse(scheduled.active(21600));
        assertFalse(scheduled.active(86400));
    }

    @Test void scheduleRequiresRecordingTime()
    {
        assertThrows(IllegalArgumentException.class, () -> new Rules(
            "Gate", new Box(0, 0, 1, 1), null, LocalTime.NOON,
            LocalTime.MIDNIGHT, Set.of(), 1, 3, 3, 10, 2, .08, .45));
    }

    @Test void lowConfidenceAndOutsideAreaAreIgnored()
    {
        var engine = new RuleEngine(rules());
        for (int i = 0; i < 5; i++)
        {
            engine.accept(i, List.of(new Detection("person", .2,
                person().box())));
        }
        assertTrue(engine.events().isEmpty());
        assertThrows(IllegalArgumentException.class,
            () -> new Box(Double.NaN, 0, .2, .2));
    }
}

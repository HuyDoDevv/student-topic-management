package vn.edu.topicregistration.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

@Component
public class ProjectTime {
    private final Clock clock;

    public ProjectTime(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public Instant toStoredInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(TimeConfig.BUSINESS_ZONE).toInstant();
    }

    public ZonedDateTime toBusinessTime(Instant instant) {
        return instant.atZone(TimeConfig.BUSINESS_ZONE);
    }

    /** A registration window includes its start and excludes its end. */
    public boolean isOpen(Instant startInclusive, Instant endExclusive) {
        Instant current = now();
        return !current.isBefore(startInclusive) && current.isBefore(endExclusive);
    }
}

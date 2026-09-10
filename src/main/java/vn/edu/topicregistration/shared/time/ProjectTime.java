package vn.edu.topicregistration.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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

    public LocalDate today() {
        return now().atZone(TimeConfig.BUSINESS_ZONE).toLocalDate();
    }

    public Instant toStartOfDay(LocalDate localDate) {
        return localDate.atStartOfDay(TimeConfig.BUSINESS_ZONE).toInstant();
    }

    public Instant toEndOfDay(LocalDate localDate) {
        return localDate.plusDays(1)
                .atStartOfDay(TimeConfig.BUSINESS_ZONE)
                .toInstant()
                .minus(1, ChronoUnit.MICROS);
    }

    public LocalDate toBusinessDate(Instant instant) {
        return toBusinessTime(instant).toLocalDate();
    }

    public ZonedDateTime toBusinessTime(Instant instant) {
        return instant.atZone(TimeConfig.BUSINESS_ZONE);
    }

    /** A date-based registration window includes both stored calendar-day boundaries. */
    public boolean isOpen(Instant startInclusive, Instant endInclusive) {
        Instant current = now();
        return !current.isBefore(startInclusive) && !current.isAfter(endInclusive);
    }
}

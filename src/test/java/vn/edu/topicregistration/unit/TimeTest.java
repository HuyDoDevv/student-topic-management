package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.util.ProjectTime;
import vn.edu.topicregistration.config.TimeConfig;

class TimeTest {
    @Test
    void convertsVietnamCalendarDayToUtcBoundariesAndBack() {
        ProjectTime time = at("2026-09-09T03:00:00Z");
        LocalDate formValue = LocalDate.of(2026, 9, 9);

        Instant storedStart = time.toStartOfDay(formValue);
        Instant storedEnd = time.toEndOfDay(formValue);

        assertEquals(Instant.parse("2026-09-08T17:00:00Z"), storedStart);
        assertEquals(Instant.parse("2026-09-09T16:59:59.999999Z"), storedEnd);
        assertEquals(formValue, time.toBusinessDate(storedStart));
        assertEquals(formValue, time.toBusinessDate(storedEnd));
        assertEquals(formValue, time.today());
        assertEquals(TimeConfig.BUSINESS_ZONE, time.toBusinessTime(storedStart).getZone());
    }

    @Test
    void registrationWindowIncludesBothCalendarDayBoundaries() {
        Instant start = Instant.parse("2026-09-09T03:00:00Z");
        Instant end = Instant.parse("2026-09-09T04:00:00Z");

        assertFalse(at("2026-09-09T02:59:59.999999Z").isOpen(start, end));
        assertTrue(at("2026-09-09T03:00:00Z").isOpen(start, end));
        assertTrue(at("2026-09-09T03:59:59.999999Z").isOpen(start, end));
        assertTrue(at("2026-09-09T04:00:00Z").isOpen(start, end));
        assertFalse(at("2026-09-09T04:00:00.000001Z").isOpen(start, end));
    }

    private ProjectTime at(String instant) {
        return new ProjectTime(Clock.fixed(Instant.parse(instant), TimeConfig.BUSINESS_ZONE));
    }
}

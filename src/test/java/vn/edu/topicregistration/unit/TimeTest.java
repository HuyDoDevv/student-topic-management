package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.shared.time.ProjectTime;
import vn.edu.topicregistration.shared.time.TimeConfig;

class TimeTest {
    @Test
    void convertsVietnamFormTimeToUtcAndBack() {
        ProjectTime time = at("2026-09-09T03:00:00Z");
        LocalDateTime formValue = LocalDateTime.of(2026, 9, 9, 10, 0);

        Instant stored = time.toStoredInstant(formValue);

        assertEquals(Instant.parse("2026-09-09T03:00:00Z"), stored);
        assertEquals(formValue, time.toBusinessTime(stored).toLocalDateTime());
        assertEquals(TimeConfig.BUSINESS_ZONE, time.toBusinessTime(stored).getZone());
    }

    @Test
    void registrationWindowIncludesStartAndExcludesEnd() {
        Instant start = Instant.parse("2026-09-09T03:00:00Z");
        Instant end = Instant.parse("2026-09-09T04:00:00Z");

        assertFalse(at("2026-09-09T02:59:59.999999Z").isOpen(start, end));
        assertTrue(at("2026-09-09T03:00:00Z").isOpen(start, end));
        assertTrue(at("2026-09-09T03:59:59.999999Z").isOpen(start, end));
        assertFalse(at("2026-09-09T04:00:00Z").isOpen(start, end));
    }

    private ProjectTime at(String instant) {
        return new ProjectTime(Clock.fixed(Instant.parse(instant), TimeConfig.BUSINESS_ZONE));
    }
}

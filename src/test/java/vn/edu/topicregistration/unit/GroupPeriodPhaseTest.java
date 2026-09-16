package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.service.GroupService.StudentPeriodPhase;

class GroupPeriodPhaseTest {
    private static final Instant START = Instant.parse("2026-09-16T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-16T23:59:59Z");

    @Test
    void separatesUpcomingOpenAndEndedAtInclusiveBoundaries() {
        assertEquals(StudentPeriodPhase.UPCOMING,
                StudentPeriodPhase.at(START.minusNanos(1), START, END));
        assertEquals(StudentPeriodPhase.OPEN,
                StudentPeriodPhase.at(START, START, END));
        assertEquals(StudentPeriodPhase.OPEN,
                StudentPeriodPhase.at(END, START, END));
        assertEquals(StudentPeriodPhase.ENDED,
                StudentPeriodPhase.at(END.plusNanos(1), START, END));
    }
}

package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.service.GroupService.StudentPeriodPhase;
import vn.edu.topicregistration.service.GroupService.GroupPeriodGroupView;
import vn.edu.topicregistration.service.GroupService.GroupPeriodView;
import vn.edu.topicregistration.service.GroupService.StudentGroupRole;

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

    @Test
    void groupSummaryKeepsRoleAttachedToItsPeriod() {
        GroupPeriodView leaderPeriod = new GroupPeriodView(
                1L, "Đợt một", "Bộ môn", START, END, StudentPeriodPhase.OPEN,
                new GroupPeriodGroupView(11L, StudentGroupRole.LEADER, null), false);
        GroupPeriodView memberPeriod = new GroupPeriodView(
                2L, "Đợt hai", "Bộ môn", START, END, StudentPeriodPhase.OPEN,
                new GroupPeriodGroupView(22L, StudentGroupRole.MEMBER, null), false);
        GroupPeriodView emptyPeriod = new GroupPeriodView(
                3L, "Đợt ba", "Bộ môn", START, END, StudentPeriodPhase.OPEN,
                null, false);

        assertTrue(leaderPeriod.hasGroup());
        assertEquals(StudentGroupRole.LEADER, leaderPeriod.group().role());
        assertTrue(memberPeriod.hasGroup());
        assertEquals(StudentGroupRole.MEMBER, memberPeriod.group().role());
        assertFalse(emptyPeriod.hasGroup());
    }
}

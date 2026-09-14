package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.shared.dashboard.DashboardQuery;
import vn.edu.topicregistration.shared.dashboard.DashboardQuery.ActiveRegistration;
import vn.edu.topicregistration.shared.dashboard.DashboardService;
import vn.edu.topicregistration.shared.dashboard.DashboardView;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.shared.time.ProjectTime;

class DashboardServiceTest {
    AuthorizationPolicy policy;
    DashboardQuery query;
    ProjectTime time;
    DashboardService service;

    @BeforeEach
    void setUp() {
        policy = mock(AuthorizationPolicy.class);
        query = mock(DashboardQuery.class);
        time = mock(ProjectTime.class);
        service = new DashboardService(policy, query, time);
    }

    @Test
    void deanLecturerUsesDeanOnlyAndFacultyScope() {
        when(policy.currentActor()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.DEAN, RoleCode.LECTURER), 11L, 21L));
        when(query.countFacultyTopics(21L, "PENDING_APPROVAL")).thenReturn(2L);
        when(query.countFacultyPendingRegistrations(21L)).thenReturn(1L);

        DashboardView view = service.current();

        assertEquals("Trưởng khoa", view.audience());
        assertEquals(2, view.metrics().getFirst().value());
        assertEquals("Có 1 đăng ký chờ xét", view.focus().title());
        verify(query).countFacultyTopics(21L, "APPROVED");
        verify(query).countFacultyPeriods(21L);
        verifyNoInteractions(time);
    }

    @Test
    void lecturerCountsOnlyOwnedTopicsInDepartmentAndFacultyIncludingZero() {
        when(policy.currentActor()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.LECTURER), 11L, 21L));
        when(query.countLecturerTopics(7L, 11L, 21L, "DRAFT")).thenReturn(1L);

        DashboardView view = service.current();

        assertEquals("Giảng viên", view.audience());
        assertEquals(0, view.metrics().get(1).value());
        assertEquals("Có 1 đề tài nháp", view.focus().title());
        verify(query).countLecturerTopics(7L, 11L, 21L, "PUBLISHED");
    }

    @Test
    void studentSeesOwnAcceptedRegistrationFromSnapshot() {
        Instant now = Instant.parse("2026-09-14T03:00:00Z");
        when(policy.currentActor()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.STUDENT), 11L, 21L));
        when(time.now()).thenReturn(now);
        when(query.countStudentRegistrations(7L, 21L, "ACCEPTED")).thenReturn(1L);
        when(query.latestStudentRegistration(7L, 21L)).thenReturn(Optional.of(
                new ActiveRegistration(42L, "Đề tài của nhóm", "ACCEPTED")));

        DashboardView view = service.current();

        assertEquals("Sinh viên", view.audience());
        assertEquals(1, view.metrics().getLast().value());
        assertEquals("ĐĂNG KÝ THÀNH CÔNG", view.focus().label());
        assertEquals("/registrations/42", view.focus().href());
        verify(query).countStudentInvitations(7L, 21L, now);
        verify(query).countStudentGroups(7L, 21L);
    }

    @Test
    void studentWithoutRegistrationSeesInvitationAction() {
        Instant now = Instant.parse("2026-09-14T03:00:00Z");
        when(policy.currentActor()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.STUDENT), 11L, 21L));
        when(time.now()).thenReturn(now);
        when(query.countStudentInvitations(7L, 21L, now)).thenReturn(1L);
        when(query.latestStudentRegistration(7L, 21L)).thenReturn(Optional.empty());

        DashboardView view = service.current();

        assertEquals("Bạn có lời mời vào nhóm", view.focus().title());
        assertEquals("/group-invitations", view.focus().href());
    }

    @Test
    void adminCannotReadAcademicCounters() {
        when(policy.currentActor()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.ADMIN), null, null));

        DashboardView view = service.current();

        assertEquals("Quản trị sự cố", view.audience());
        assertEquals(0, view.metrics().size());
        verifyNoInteractions(query, time);
    }

    @Test
    void missingScopeFailsClosedBeforeQuery() {
        when(policy.currentActor()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.LECTURER), null, 21L));

        assertThrows(AccessDeniedException.class, service::current);
        verifyNoInteractions(query, time);
    }
}

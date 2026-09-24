package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.service.ActiveRegistrationQuery;
import vn.edu.topicregistration.service.ActiveRegistrationQuery.ActiveRegistration;
import vn.edu.topicregistration.service.ActiveRegistrationQuery.ActiveRegistrationStatus;
import vn.edu.topicregistration.service.GroupService;
import vn.edu.topicregistration.repository.GroupInvitationRepository;
import vn.edu.topicregistration.entity.GroupMembershipEntity;
import vn.edu.topicregistration.repository.GroupMembershipRepository;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.repository.StudentGroupRepository;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.service.ActiveRegistrationQueryAdapter;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.service.NotificationService;

class GroupRegistrationFeedbackTest {
    private static final Instant NOW = Instant.parse("2026-09-13T01:00:00Z");

    @Test
    void normalMemberReceivesAcceptedRegistrationSummaryOnGroupPage() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        StudentGroupRepository groups = mock(StudentGroupRepository.class);
        GroupMembershipRepository memberships = mock(GroupMembershipRepository.class);
        GroupInvitationRepository invitations = mock(GroupInvitationRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        ActiveRegistrationQuery activeRegistrations = mock(ActiveRegistrationQuery.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        GroupService service = new GroupService(
                periods,
                groups,
                memberships,
                invitations,
                accounts,
                activeRegistrations,
                authorization,
                mock(NotificationService.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        UserAccountEntity leader = account(32L, "student-2", "Sinh viên 2");
        UserAccountEntity member = account(31L, "student-1", "Sinh viên 1");
        GroupMembershipEntity leaderMembership = membership(leader, 1);
        GroupMembershipEntity memberMembership = membership(member, 2);

        when(authorization.requireRole(RoleCode.STUDENT)).thenReturn(new ActorIdentity(
                31L, Set.of(RoleCode.STUDENT), 3L, 7L));
        when(groups.findByIdVisibleToStudent(21L, 31L)).thenReturn(Optional.of(group));
        when(group.getId()).thenReturn(21L);
        when(group.getPeriod()).thenReturn(period);
        when(group.getLeader()).thenReturn(leader);
        when(period.getId()).thenReturn(1L);
        when(period.getStudentStart()).thenReturn(NOW.minusSeconds(60));
        when(period.getStudentEnd()).thenReturn(NOW.plusSeconds(60));
        when(memberships.findAllByGroupIdOrderBySlot(21L)).thenReturn(List.of(
                leaderMembership, memberMembership));
        when(activeRegistrations.findForGroup(1L, 21L)).thenReturn(Optional.of(
                new ActiveRegistration(
                        51L,
                        "Hệ thống quản lý đăng ký đề tài",
                        ActiveRegistrationStatus.ACCEPTED)));
        when(invitations.countByGroupIdAndStatus(
                eq(21L), eq(vn.edu.topicregistration.entity.GroupInvitationStatus.PENDING)))
                .thenReturn(0L);

        var view = service.getVisible(21L);

        assertFalse(view.canManage());
        assertTrue(view.activeRegistration());
        assertFalse(view.canLeave());
        assertNotNull(view.registration());
        assertEquals(51L, view.registration().id());
        assertEquals("Hệ thống quản lý đăng ký đề tài", view.registration().topicTitle());
        assertTrue(view.registration().accepted());
        assertEquals("Đã chấp nhận", view.registration().statusLabel());
    }

    @Test
    void adapterReadsTheAcceptedRegistrationAndItsTopic() {
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        RegistrationEntity registration = mock(RegistrationEntity.class);
        TopicEntity topic = mock(TopicEntity.class);
        ActiveRegistrationQueryAdapter adapter = new ActiveRegistrationQueryAdapter(registrations);

        when(registrations.findFirstByPeriodIdAndGroupIdAndStatusInOrderBySubmittedAtDescIdDesc(
                eq(1L), eq(21L), anyCollection())).thenReturn(Optional.of(registration));
        when(registration.getId()).thenReturn(51L);
        when(registration.getTopic()).thenReturn(topic);
        when(registration.getStatus()).thenReturn(RegistrationStatus.ACCEPTED);
        when(topic.getTitle()).thenReturn("Hệ thống quản lý đăng ký đề tài");

        ActiveRegistration result = adapter.findForGroup(1L, 21L).orElseThrow();

        assertEquals(51L, result.id());
        assertEquals("Hệ thống quản lý đăng ký đề tài", result.topicTitle());
        assertEquals(ActiveRegistrationStatus.ACCEPTED, result.status());
    }

    private static GroupMembershipEntity membership(UserAccountEntity student, int slot) {
        GroupMembershipEntity membership = mock(GroupMembershipEntity.class);
        when(membership.getStudent()).thenReturn(student);
        when(membership.getSlot()).thenReturn(slot);
        return membership;
    }

    private static UserAccountEntity account(long id, String username, String fullName) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(id);
        when(account.getUsername()).thenReturn(username);
        when(account.getFullName()).thenReturn(fullName);
        return account;
    }
}

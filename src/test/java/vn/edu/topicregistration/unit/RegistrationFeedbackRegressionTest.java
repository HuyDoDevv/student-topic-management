package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.service.RegistrationRules;
import vn.edu.topicregistration.service.RegistrationRules.Failure;
import vn.edu.topicregistration.service.RegistrationRules.RegistrationRuleException;
import vn.edu.topicregistration.service.RegistrationRules.SubmissionContext;
import vn.edu.topicregistration.repository.GroupInvitationRepository;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.repository.StudentGroupRepository;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.service.RegistrationService;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationMemberEntity;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.repository.TopicRepository;

class RegistrationFeedbackRegressionTest {
    private static final Instant START = Instant.parse("2026-09-12T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-12T01:00:00Z");
    private static final Instant END = Instant.parse("2026-09-12T02:00:00Z");

    @Test
    void onlyACompleteThreeStudentGroupCanSubmit() {
        assertDoesNotThrow(() -> RegistrationRules.validateSubmit(context(3)));

        RegistrationRuleException oneStudent = assertThrows(
                RegistrationRuleException.class,
                () -> RegistrationRules.validateSubmit(context(1)));
        RegistrationRuleException twoStudents = assertThrows(
                RegistrationRuleException.class,
                () -> RegistrationRules.validateSubmit(context(2)));

        assertEquals(Failure.GROUP_INCOMPLETE, oneStudent.failure());
        assertEquals(Failure.GROUP_INCOMPLETE, twoStudents.failure());
    }

    @Test
    void topicCanReceiveRegistrationsUntilConfiguredCapacityIsFull() {
        assertDoesNotThrow(() -> RegistrationRules.validateSubmit(context(3, false)));

        RegistrationRuleException full = assertThrows(
                RegistrationRuleException.class,
                () -> RegistrationRules.validateSubmit(context(3, true)));

        assertEquals(Failure.TOPIC_TAKEN, full.failure());
    }

    @Test
    void submissionWorkspaceShowsTopicUntilAcceptedGroupsReachCapacity() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        StudentGroupRepository groups = mock(StudentGroupRepository.class);
        GroupInvitationRepository invitations = mock(GroupInvitationRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        RegistrationMemberRepository snapshots = mock(RegistrationMemberRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        RegistrationService service = service(
                periods, groups, invitations, topics, registrations, snapshots, authorization);
        FacultyEntity faculty = mock(FacultyEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        UserAccountEntity leader = account(31L, "leader", "Trưởng nhóm");
        TopicEntity topic = mock(TopicEntity.class);
        when(faculty.getId()).thenReturn(7L);
        when(faculty.getName()).thenReturn("Khoa CNTT");
        when(period.getId()).thenReturn(1L);
        when(period.getName()).thenReturn("Đợt 1");
        when(period.getFaculty()).thenReturn(faculty);
        when(group.getId()).thenReturn(21L);
        when(group.getPeriod()).thenReturn(period);
        when(group.getLeader()).thenReturn(leader);
        when(group.getMembers()).thenReturn(List.of());
        when(group.isActive()).thenReturn(true);
        when(groups.findById(21L)).thenReturn(Optional.of(group));
        when(authorization.requireStudentOfFaculty(7L)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.STUDENT), 7L));
        when(registrations
                .findFirstByPeriodIdAndGroupIdAndStatusInOrderBySubmittedAtDescIdDesc(
                        1L, 21L, List.of(RegistrationStatus.PENDING, RegistrationStatus.ACCEPTED)))
                .thenReturn(Optional.empty());
        when(topics.findAllVisibleByPeriodIdAndStatusIn(1L, List.of("PUBLISHED")))
                .thenReturn(List.of(topic));
        when(topic.getId()).thenReturn(41L);
        when(topic.getTitle()).thenReturn("Đề tài hai nhóm");
        when(topic.getPeriod()).thenReturn(period);
        when(topic.getSupervisors()).thenReturn(List.of());
        when(topic.getMaxGroupCount()).thenReturn(2);
        when(registrations.countByTopicIdAndStatus(41L, RegistrationStatus.ACCEPTED))
                .thenReturn(1L);

        var oneSlotLeft = service.submissionWorkspace(21L);

        assertEquals(1, oneSlotLeft.topics().size());
        assertEquals(1, oneSlotLeft.topics().getFirst().remainingGroupCount());

        when(registrations.countByTopicIdAndStatus(41L, RegistrationStatus.ACCEPTED))
                .thenReturn(2L);

        assertEquals(List.of(), service.submissionWorkspace(21L).topics());
    }

    @Test
    void forgedSubmissionCannotExceedTopicCapacity() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        StudentGroupRepository groups = mock(StudentGroupRepository.class);
        GroupInvitationRepository invitations = mock(GroupInvitationRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        RegistrationMemberRepository snapshots = mock(RegistrationMemberRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        RegistrationService service = service(
                periods, groups, invitations, topics, registrations, snapshots, authorization);
        FacultyEntity faculty = mock(FacultyEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        TopicEntity topic = mock(TopicEntity.class);
        UserAccountEntity leader = account(31L, "leader", "Trưởng nhóm");
        when(faculty.getId()).thenReturn(7L);
        when(period.getId()).thenReturn(1L);
        when(period.getFaculty()).thenReturn(faculty);
        when(period.getStudentStart()).thenReturn(START);
        when(period.getStudentEnd()).thenReturn(END);
        when(groups.findPeriodIdByGroupId(21L)).thenReturn(Optional.of(1L));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(authorization.requireStudentOfFaculty(7L)).thenReturn(
                new ActorIdentity(31L, Set.of(RoleCode.STUDENT), 7L));
        when(groups.findByIdForUpdate(21L)).thenReturn(Optional.of(group));
        when(group.getPeriod()).thenReturn(period);
        when(group.getLeader()).thenReturn(leader);
        when(group.isActive()).thenReturn(true);
        List<vn.edu.topicregistration.entity.GroupMembershipEntity> members = List.of(
                membership(leader),
                membership(account(32L, "member-2", "Thành viên 2")),
                membership(account(33L, "member-3", "Thành viên 3")));
        when(group.getMembers()).thenReturn(members);
        when(topics.findByIdForUpdate(41L)).thenReturn(Optional.of(topic));
        when(topic.getId()).thenReturn(41L);
        when(topic.getPeriod()).thenReturn(period);
        when(topic.getStatus()).thenReturn("PUBLISHED");
        when(topic.getMaxGroupCount()).thenReturn(2);
        when(registrations.countByTopicIdAndStatus(41L, RegistrationStatus.ACCEPTED))
                .thenReturn(2L);

        ApplicationException full = assertThrows(ApplicationException.class,
                () -> service.submit(21L, 0L, 41L));

        assertEquals(ApplicationErrorCode.TOPIC_TAKEN, full.code());
        verify(registrations, never()).saveAndFlush(any());
    }

    @Test
    void withdrawnRegistrationKeepsSubmittedRosterAfterCurrentGroupChanges() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        StudentGroupRepository groups = mock(StudentGroupRepository.class);
        GroupInvitationRepository invitations = mock(GroupInvitationRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        RegistrationMemberRepository snapshots = mock(RegistrationMemberRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        RegistrationService service = new RegistrationService(
                periods,
                groups,
                invitations,
                topics,
                registrations,
                snapshots,
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(vn.edu.topicregistration.service.ReportService.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));

        FacultyEntity faculty = mock(FacultyEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        StudentGroupEntity currentGroup = mock(StudentGroupEntity.class);
        TopicEntity topic = mock(TopicEntity.class);
        UserAccountEntity author = account(81L, "lecturer", "Giảng viên");
        UserAccountEntity currentLeader = account(99L, "new-leader", "Trưởng nhóm mới");
        RegistrationEntity registration = mock(RegistrationEntity.class);

        when(faculty.getId()).thenReturn(7L);
        when(period.getId()).thenReturn(1L);
        when(period.getName()).thenReturn("Đợt 1");
        when(period.getFaculty()).thenReturn(faculty);
        when(period.getStudentStart()).thenReturn(START);
        when(period.getStudentEnd()).thenReturn(END);
        when(currentGroup.getId()).thenReturn(21L);
        when(currentGroup.getLeader()).thenReturn(currentLeader);
        when(currentGroup.getMembers()).thenReturn(List.of());
        when(topic.getId()).thenReturn(41L);
        when(topic.getTitle()).thenReturn("Đề tài đã gửi");
        when(topic.getPeriod()).thenReturn(period);
        when(topic.getAuthor()).thenReturn(author);
        when(topic.getSupervisors()).thenReturn(List.of());
        when(registration.getId()).thenReturn(51L);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getGroup()).thenReturn(currentGroup);
        when(registration.getTopic()).thenReturn(topic);
        when(registration.getStatus()).thenReturn(RegistrationStatus.WITHDRAWN);
        when(registration.getSubmittedAt()).thenReturn(NOW.minusSeconds(60));
        when(registration.getWithdrawnAt()).thenReturn(NOW);
        when(registrations.findDetailedById(51L)).thenReturn(Optional.of(registration));
        when(authorization.currentActor()).thenReturn(new ActorIdentity(
                31L, Set.of(RoleCode.STUDENT), 7L));
        when(snapshots.existsByRegistrationIdAndStudentId(51L, 31L)).thenReturn(true);
        List<RegistrationMemberEntity> submittedRoster = List.of(
                snapshot(31L, "student-1", "Sinh viên 1", true),
                snapshot(32L, "student-2", "Sinh viên 2", false),
                snapshot(33L, "student-3", "Sinh viên 3", false));
        when(snapshots.findAllByRegistrationId(51L)).thenReturn(submittedRoster);

        var detail = service.getVisible(51L);

        assertEquals(3, detail.members().size());
        assertEquals(31L, detail.members().getFirst().id());
        assertTrue(detail.members().getFirst().leader());
    }

    private static RegistrationMemberEntity snapshot(
            long id, String username, String fullName, boolean leader) {
        RegistrationMemberEntity snapshot = mock(RegistrationMemberEntity.class);
        UserAccountEntity student = account(id, username, fullName);
        when(snapshot.getStudent()).thenReturn(student);
        when(snapshot.isLeaderAtSubmission()).thenReturn(leader);
        return snapshot;
    }

    private static vn.edu.topicregistration.entity.GroupMembershipEntity membership(
            UserAccountEntity student) {
        var membership = mock(vn.edu.topicregistration.entity.GroupMembershipEntity.class);
        when(membership.getStudent()).thenReturn(student);
        return membership;
    }

    private static RegistrationService service(
            RegistrationPeriodRepository periods,
            StudentGroupRepository groups,
            GroupInvitationRepository invitations,
            TopicRepository topics,
            RegistrationRepository registrations,
            RegistrationMemberRepository snapshots,
            AuthorizationPolicy authorization) {
        return new RegistrationService(
                periods,
                groups,
                invitations,
                topics,
                registrations,
                snapshots,
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(vn.edu.topicregistration.service.ReportService.class),
                mock(org.springframework.beans.factory.ObjectProvider.class));
    }

    private static UserAccountEntity account(long id, String username, String fullName) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(id);
        when(account.getUsername()).thenReturn(username);
        when(account.getFullName()).thenReturn(fullName);
        return account;
    }

    private static SubmissionContext context(int memberCount) {
        return context(memberCount, false);
    }

    private static SubmissionContext context(int memberCount, boolean topicFull) {
        return new SubmissionContext(
                11L,
                11L,
                memberCount,
                true,
                true,
                false,
                false,
                true,
                true,
                topicFull,
                NOW,
                START,
                END);
    }
}

package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import vn.edu.topicregistration.entity.DepartmentEntity;
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
                Clock.fixed(NOW, ZoneOffset.UTC));

        FacultyEntity faculty = mock(FacultyEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        StudentGroupEntity currentGroup = mock(StudentGroupEntity.class);
        TopicEntity topic = mock(TopicEntity.class);
        DepartmentEntity department = mock(DepartmentEntity.class);
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
        when(department.getName()).thenReturn("Công nghệ phần mềm");
        when(topic.getId()).thenReturn(41L);
        when(topic.getTitle()).thenReturn("Đề tài đã gửi");
        when(topic.getDepartment()).thenReturn(department);
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
                31L, Set.of(RoleCode.STUDENT), 3L, 7L));
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

    private static UserAccountEntity account(long id, String username, String fullName) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(id);
        when(account.getUsername()).thenReturn(username);
        when(account.getFullName()).thenReturn(fullName);
        return account;
    }

    private static SubmissionContext context(int memberCount) {
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
                false,
                NOW,
                START,
                END);
    }
}

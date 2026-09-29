package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.TopicRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.TopicAuthorService;
import vn.edu.topicregistration.service.TopicAuthorService.TopicDraftCommand;

class TopicAuthorServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-16T03:00:00Z");

    @Test
    void createDraftIsRejectedAfterLecturerWindowAndDoesNotPersist() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        EntityManager entityManager = mock(EntityManager.class);
        TopicAuthorService service = new TopicAuthorService(
                periods,
                topics,
                accounts,
                authorization,
                entityManager,
                Clock.fixed(NOW, ZoneOffset.UTC));

        FacultyEntity faculty = mock(FacultyEntity.class);
        UserAccountEntity author = mock(UserAccountEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(faculty.getId()).thenReturn(21L);
        when(author.getId()).thenReturn(7L);
        when(author.getFaculty()).thenReturn(faculty);
        when(author.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(author.isEnabled()).thenReturn(true);
        when(author.getUsername()).thenReturn("lecturer");
        when(period.getId()).thenReturn(1L);
        when(period.getFaculty()).thenReturn(faculty);
        when(period.getLecturerStart()).thenReturn(NOW.minusSeconds(600));
        when(period.getLecturerEnd()).thenReturn(NOW.minusSeconds(1));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(authorization.requireTopicAuthoringLecturer()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.LECTURER), 21L));
        when(entityManager.find(UserAccountEntity.class, 7L)).thenReturn(author);
        when(topics.saveAndFlush(any(TopicEntity.class))).thenAnswer(call -> call.getArgument(0));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> service.createDraft(1L, new TopicDraftCommand(
                        "Tên", "Mô tả", "Mục tiêu", "Yêu cầu", "Kết quả", List.of())));

        assertEquals(ApplicationErrorCode.WINDOW_CLOSED, failure.code());
        verify(topics, never()).saveAndFlush(any());
    }

    @Test
    void createFormContextIsRejectedOutsideLecturerWindow() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        EntityManager entityManager = mock(EntityManager.class);
        TopicAuthorService service = new TopicAuthorService(
                periods,
                topics,
                accounts,
                authorization,
                entityManager,
                Clock.fixed(NOW, ZoneOffset.UTC));

        FacultyEntity faculty = mock(FacultyEntity.class);
        UserAccountEntity author = mock(UserAccountEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(faculty.getId()).thenReturn(21L);
        when(author.getId()).thenReturn(7L);
        when(author.getUsername()).thenReturn("lecturer");
        when(author.getFaculty()).thenReturn(faculty);
        when(period.getId()).thenReturn(1L);
        when(period.getName()).thenReturn("Đợt cũ");
        when(period.getFaculty()).thenReturn(faculty);
        when(period.getLecturerStart()).thenReturn(NOW.minusSeconds(600));
        when(period.getLecturerEnd()).thenReturn(NOW.minusSeconds(1));
        when(period.getStudentEnd()).thenReturn(NOW.plusSeconds(600));
        when(periods.findByIdAndFacultyId(1L, 21L)).thenReturn(Optional.of(period));
        when(authorization.requireTopicAuthoringLecturer()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.LECTURER), 21L));
        when(entityManager.find(UserAccountEntity.class, 7L)).thenReturn(author);
        when(accounts.findEnabledByFacultyAndRole(21L, RoleCode.LECTURER))
                .thenReturn(List.of(author));

        ApplicationException failure = assertThrows(
                ApplicationException.class,
                () -> service.createFormContext(1L));

        assertEquals(ApplicationErrorCode.WINDOW_CLOSED, failure.code());
    }
}

package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.PeriodController;
import vn.edu.topicregistration.controller.TopicReviewController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.TopicRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.PeriodService;
import vn.edu.topicregistration.service.RegistrationService;
import vn.edu.topicregistration.service.TopicReviewService;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;
import vn.edu.topicregistration.util.ProjectTime;

class TopicReviewServiceTest {
    private final RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
    private final TopicRepository topics = mock(TopicRepository.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TopicReviewService service = new TopicReviewService(
            periods, topics, registrations, authorization, notifications);

    @Test
    void deanConfiguresCapacityWhenApprovingTopic() {
        RegistrationPeriodEntity period = period(10L);
        TopicEntity topic = pendingTopic(period);
        stubLockedTopic(period, topic);

        var result = service.approve(5L, 0L, 2);

        assertEquals(TopicStatus.APPROVED, result.status());
        assertEquals("APPROVED", topic.getStatus());
        assertEquals(2, topic.getMaxGroupCount());
        verify(topics).flush();
    }

    @Test
    void approvalRejectsCapacityOutsideOneToFive() {
        RegistrationPeriodEntity period = period(10L);
        TopicEntity topic = pendingTopic(period);
        stubLockedTopic(period, topic);

        ApplicationException zero = assertThrows(ApplicationException.class,
                () -> service.approve(5L, 0L, 0));
        ApplicationException six = assertThrows(ApplicationException.class,
                () -> service.approve(5L, 0L, 6));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, zero.code());
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, six.code());
        assertEquals("PENDING_APPROVAL", topic.getStatus());
        verify(topics, never()).flush();
        verifyNoInteractions(notifications);
    }

    @Test
    void workspaceUsesPersistedPeriodDepartmentForQueryAndCounts() {
        RegistrationPeriodEntity period = period(10L);
        when(authorization.requireRole(RoleCode.DEAN))
                .thenReturn(new ActorIdentity(7L, Set.of(RoleCode.DEAN), 20L));
        when(periods.findByIdAndFacultyId(1L, 20L)).thenReturn(Optional.of(period));
        when(topics.findAllVisibleForReview(1L, 10L,
                List.of("PENDING_APPROVAL", "APPROVED", "PUBLISHED")))
                .thenReturn(List.of());

        TopicReviewService.ReviewWorkspace workspace = service.reviewWorkspace(1L);

        assertEquals(0, workspace.pendingCount());
        verify(topics).findAllVisibleForReview(1L, 10L,
                List.of("PENDING_APPROVAL", "APPROVED", "PUBLISHED"));
        verify(topics, never()).findAllVisibleByPeriodIdAndStatusIn(
                1L, List.of("PENDING_APPROVAL", "APPROVED", "PUBLISHED"));
    }

    @Test
    void workspaceShowsConfiguredCapacityAndAcceptedGroupCount() {
        RegistrationPeriodEntity period = period(10L);
        TopicEntity topic = mock(TopicEntity.class);
        UserAccountEntity author = mock(UserAccountEntity.class);
        DepartmentEntity department = period.getDepartment();
        when(authorization.requireRole(RoleCode.DEAN))
                .thenReturn(new ActorIdentity(7L, Set.of(RoleCode.DEAN), 20L));
        when(periods.findByIdAndFacultyId(1L, 20L)).thenReturn(Optional.of(period));
        when(topic.getId()).thenReturn(5L);
        when(topic.getPeriod()).thenReturn(period);
        when(topic.getDepartment()).thenReturn(department);
        when(topic.getAuthor()).thenReturn(author);
        when(author.getFullName()).thenReturn("Giảng viên A");
        when(topic.getStatus()).thenReturn(TopicStatus.PUBLISHED.name());
        when(topic.getMaxGroupCount()).thenReturn(2);
        when(topic.getSupervisors()).thenReturn(List.of());
        when(topics.findAllVisibleForReview(1L, 10L,
                List.of("PENDING_APPROVAL", "APPROVED", "PUBLISHED")))
                .thenReturn(List.of(topic));
        when(registrations.countByTopicIdAndStatus(5L,
                vn.edu.topicregistration.entity.RegistrationStatus.ACCEPTED))
                .thenReturn(1L);

        var visible = service.reviewWorkspace(1L).topics().getFirst();

        assertEquals(2, visible.maxGroupCount());
        assertEquals(1, visible.acceptedGroupCount());
    }

    @Test
    void legacyPeriodWithoutDepartmentHasEmptyReviewWorkspace() {
        RegistrationPeriodEntity period = period(10L);
        when(period.getDepartment()).thenReturn(null);
        when(authorization.requireRole(RoleCode.DEAN))
                .thenReturn(new ActorIdentity(7L, Set.of(RoleCode.DEAN), 20L));
        when(periods.findByIdAndFacultyId(1L, 20L)).thenReturn(Optional.of(period));

        TopicReviewService.ReviewWorkspace workspace = service.reviewWorkspace(1L);

        assertEquals(List.of(), workspace.topics());
        verifyNoInteractions(topics);
    }

    @Test
    void mismatchedDepartmentCannotBeApprovedOrRejected() {
        RegistrationPeriodEntity period = period(10L);
        TopicEntity topic = mock(TopicEntity.class);
        DepartmentEntity otherDepartment = mock(DepartmentEntity.class);
        when(otherDepartment.getId()).thenReturn(11L);
        when(topic.getPeriod()).thenReturn(period);
        when(topic.getDepartment()).thenReturn(otherDepartment);
        when(topics.findPeriodIdByTopicId(5L)).thenReturn(Optional.of(1L));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(topics.findByIdForUpdate(5L)).thenReturn(Optional.of(topic));

        ApplicationException approve = assertThrows(ApplicationException.class,
                () -> service.approve(5L, 0L));
        ApplicationException reject = assertThrows(ApplicationException.class,
                () -> service.reject(5L, 0L));

        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, approve.code());
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, reject.code());
        verify(topics, never()).flush();
        verifyNoInteractions(notifications);
    }

    @Test
    void publishQueriesOnlyThePersistedPeriodDepartment() {
        RegistrationPeriodEntity period = period(10L);
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(topics.findAllApprovedByPeriodIdAndDepartmentIdForUpdate(1L, 10L))
                .thenReturn(List.of());

        assertEquals(0, service.publishApproved(1L).publishedCount());

        verify(topics).findAllApprovedByPeriodIdAndDepartmentIdForUpdate(1L, 10L);
        verify(topics, never()).flush();
    }

    @Test
    void forgedDepartmentParameterIsRejectedBeforeControllerMutation() throws Exception {
        TopicReviewService reviewService = mock(TopicReviewService.class);
        MockMvc mvc = standaloneSetup(
                new TopicReviewController(reviewService),
                new PeriodController(
                        mock(PeriodService.class), reviewService,
                        mock(RegistrationService.class), authorization,
                        mock(ProjectTime.class)))
                .setControllerAdvice(new WebExceptionHandler())
                .build();

        mvc.perform(get("/periods/1/topics/review").param("departmentId", "11"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/topics/5/approve")
                        .param("version", "0").param("departmentId", "11"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/topics/5/reject?departmentId=11").param("version", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/periods/1/topics/publish").param("departmentId", "11"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void approveRequestDefaultsCapacityToOne() throws Exception {
        TopicReviewService reviewService = mock(TopicReviewService.class);
        when(reviewService.approve(5L, 0L, 1)).thenReturn(
                new TopicReviewService.ReviewResult(
                        5L, 1L, TopicStatus.APPROVED, 1L));
        MockMvc mvc = standaloneSetup(new TopicReviewController(reviewService))
                .setControllerAdvice(new WebExceptionHandler())
                .build();

        mvc.perform(post("/topics/5/approve").param("version", "0"))
                .andExpect(status().isSeeOther());

        verify(reviewService).approve(5L, 0L, 1);
    }

    private void stubLockedTopic(RegistrationPeriodEntity period, TopicEntity topic) {
        when(topics.findPeriodIdByTopicId(5L)).thenReturn(Optional.of(1L));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(authorization.requireDeanOfFaculty(20L)).thenReturn(
                new ActorIdentity(7L, Set.of(RoleCode.DEAN), 20L));
        when(topics.findByIdForUpdate(5L)).thenReturn(Optional.of(topic));
    }

    private TopicEntity pendingTopic(RegistrationPeriodEntity period) {
        UserAccountEntity author = mock(UserAccountEntity.class);
        when(author.getId()).thenReturn(8L);
        TopicEntity topic = new TopicEntity(period, author, period.getDepartment());
        try {
            Field id = TopicEntity.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(topic, 5L);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
        topic.changeStatus(TopicStatus.PENDING_APPROVAL.name());
        return topic;
    }

    private RegistrationPeriodEntity period(long departmentId) {
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        FacultyEntity faculty = mock(FacultyEntity.class);
        DepartmentEntity department = mock(DepartmentEntity.class);
        when(period.getId()).thenReturn(1L);
        when(period.getFaculty()).thenReturn(faculty);
        when(faculty.getId()).thenReturn(20L);
        when(period.getDepartment()).thenReturn(department);
        when(department.getId()).thenReturn(departmentId);
        return period;
    }
}

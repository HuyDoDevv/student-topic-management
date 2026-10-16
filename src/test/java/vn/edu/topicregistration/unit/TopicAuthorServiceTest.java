package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.TopicAuthorController;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.TopicRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.TopicAuthorService;
import vn.edu.topicregistration.service.TopicAuthorService.TopicDraftCommand;
import vn.edu.topicregistration.service.TopicAuthorService.TopicView;
import vn.edu.topicregistration.service.TopicRules.TopicStatus;

class TopicAuthorServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-16T03:00:00Z");

    @Test
    void returnRouteRedirectsToEditWithResubmissionNotice() throws Exception {
        TopicAuthorService service = mock(TopicAuthorService.class);
        when(service.returnToDraft(5L, 2L)).thenReturn(new TopicView(
                5L, 1L, 21L, "Khoa", "Bộ môn", 7L, "Tác giả", "Tên", "Mô tả",
                "Mục tiêu", "Yêu cầu", "Kết quả", TopicStatus.DRAFT, 3L, List.of()));
        MockMvc mvc = standaloneSetup(new TopicAuthorController(service)).build();

        mvc.perform(post("/topics/5/return-to-draft").param("version", "2"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/topics/5/edit"))
                .andExpect(flash().attribute("returnToDraftNotice",
                        "Đề tài đã trở về bản nháp. Sau khi sửa, bạn cần gửi duyệt lại."));
        verify(service).returnToDraft(5L, 2L);
    }

    @Test
    void authorCanReturnApprovedTopicToDraftForResubmission() {
        Scenario scenario = openScenario();
        TopicEntity topic = approvedTopic(scenario);

        var result = scenario.service().returnToDraft(5L, 0L);

        assertEquals("DRAFT", topic.getStatus());
        assertEquals(vn.edu.topicregistration.service.TopicRules.TopicStatus.DRAFT,
                result.status());
        verify(scenario.registrations()).existsByPeriodIdAndTopicId(1L, 5L);
        verify(scenario.topics()).flush();
    }

    @Test
    void existingRegistrationBlocksReturnWithoutChangingApprovedTopic() {
        Scenario scenario = openScenario();
        TopicEntity topic = approvedTopic(scenario);
        when(scenario.registrations().existsByPeriodIdAndTopicId(1L, 5L)).thenReturn(true);

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().returnToDraft(5L, 0L));

        assertEquals(ApplicationErrorCode.TOPIC_REGISTRATION_EXISTS, failure.code());
        assertEquals("APPROVED", topic.getStatus());
        verify(scenario.topics(), never()).flush();
    }

    @Test
    void staleVersionBlocksReturnBeforeRegistrationLookup() {
        Scenario scenario = openScenario();
        TopicEntity topic = approvedTopic(scenario);

        assertThrows(OptimisticLockingFailureException.class,
                () -> scenario.service().returnToDraft(5L, 1L));

        assertEquals("APPROVED", topic.getStatus());
        verify(scenario.registrations(), never()).existsByPeriodIdAndTopicId(1L, 5L);
        verify(scenario.topics(), never()).flush();
    }

    @Test
    void staleDraftVersionBlocksBeforeLoadingSubmittedSupervisors() {
        Scenario scenario = openScenario();
        TopicEntity topic = new TopicEntity(
                scenario.period(), scenario.author(), scenario.period().getDepartment());
        when(scenario.topics().findPeriodIdByTopicId(5L)).thenReturn(Optional.of(1L));
        when(scenario.topics().findByIdForUpdate(5L)).thenReturn(Optional.of(topic));

        assertThrows(OptimisticLockingFailureException.class,
                () -> scenario.service().saveDraft(5L, 1L, completeCommand()));

        verify(scenario.authorization(), never()).requireTopicAuthor(7L, 21L);
        verify(scenario.accounts(), never()).findByIdForUpdate(7L);
        verify(scenario.topics(), never()).flush();
    }

    private static TopicEntity approvedTopic(Scenario scenario) {
        TopicEntity topic = new TopicEntity(scenario.period(), scenario.author(),
                scenario.period().getDepartment());
        topic.updateDraft("Tên", "Mô tả", "Mục tiêu", "Yêu cầu", "Kết quả");
        topic.changeStatus("APPROVED");
        when(scenario.topics().findPeriodIdByTopicId(5L)).thenReturn(Optional.of(1L));
        when(scenario.topics().findByIdForUpdate(5L)).thenReturn(Optional.of(topic));
        when(scenario.authorization().requireTopicAuthor(7L, 21L))
                .thenReturn(new ActorIdentity(7L, Set.of(RoleCode.LECTURER), 21L));
        return topic;
    }

    @Test
    void createDraftUsesAuthorsDepartmentRatherThanRequestData() {
        Scenario scenario = openScenario();
        DepartmentEntity department = mock(DepartmentEntity.class);
        when(department.getId()).thenReturn(31L);
        when(department.getFaculty()).thenReturn(scenario.faculty());
        when(scenario.author().getDepartment()).thenReturn(department);
        when(scenario.topics().saveAndFlush(any(TopicEntity.class)))
                .thenAnswer(call -> call.getArgument(0));

        scenario.service().createDraft(1L, completeCommand());

        org.mockito.ArgumentCaptor<TopicEntity> saved =
                org.mockito.ArgumentCaptor.forClass(TopicEntity.class);
        verify(scenario.topics()).saveAndFlush(saved.capture());
        assertEquals(department, saved.getValue().getDepartment());
        assertEquals(scenario.author(), saved.getValue().getAuthor());
        assertEquals(1, saved.getValue().getSupervisors().size());
        assertEquals(scenario.author(), saved.getValue().getSupervisors().getFirst().getLecturer());
    }

    @Test
    void createDraftRejectsAuthorWithoutDepartmentBeforeSaving() {
        Scenario scenario = openScenario();
        when(scenario.author().getDepartment()).thenReturn(null);

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().createDraft(1L, completeCommand()));

        assertEquals(ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED, failure.code());
        verify(scenario.topics(), never()).saveAndFlush(any());
    }

    @Test
    void createDraftRejectsDepartmentOutsidePeriodFaculty() {
        Scenario scenario = openScenario();
        DepartmentEntity department = mock(DepartmentEntity.class);
        FacultyEntity otherFaculty = mock(FacultyEntity.class);
        when(department.getId()).thenReturn(31L);
        when(otherFaculty.getId()).thenReturn(22L);
        when(department.getFaculty()).thenReturn(otherFaculty);
        when(scenario.author().getDepartment()).thenReturn(department);

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().createDraft(1L, completeCommand()));

        assertEquals(ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED, failure.code());
        verify(scenario.topics(), never()).saveAndFlush(any());
    }

    @Test
    void legacyDraftWithoutDepartmentCannotBeSubmitted() {
        Scenario scenario = openScenario();
        TopicEntity legacy = new TopicEntity(scenario.period(), scenario.author());
        legacy.updateDraft("Tên", "Mô tả", "Mục tiêu", "Yêu cầu", "Kết quả");
        legacy.replaceSupervisors(List.of(scenario.author()));
        when(scenario.topics().findPeriodIdByTopicId(5L)).thenReturn(Optional.of(1L));
        when(scenario.topics().findByIdForUpdate(5L)).thenReturn(Optional.of(legacy));
        when(scenario.authorization().requireTopicAuthor(7L, 21L))
                .thenReturn(new ActorIdentity(7L, Set.of(RoleCode.LECTURER), 21L));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().submit(5L, 0L));

        assertEquals(ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED, failure.code());
        assertEquals("DRAFT", legacy.getStatus());
        verify(scenario.topics(), never()).flush();
    }

    @Test
    void coSupervisorFromAnotherDepartmentIsRejectedBeforeDraftPersistence() {
        Scenario scenario = openScenario();
        DepartmentEntity authorDepartment = department(31L, scenario.faculty());
        DepartmentEntity otherDepartment = department(32L, scenario.faculty());
        when(scenario.author().getDepartment()).thenReturn(authorDepartment);
        UserAccountEntity otherLecturer = mock(UserAccountEntity.class);
        when(otherLecturer.getId()).thenReturn(8L);
        when(otherLecturer.getFaculty()).thenReturn(scenario.faculty());
        when(otherLecturer.getDepartment()).thenReturn(otherDepartment);
        when(otherLecturer.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(otherLecturer.isEnabled()).thenReturn(true);
        when(scenario.accounts().findByIdForUpdate(8L))
                .thenReturn(Optional.of(otherLecturer));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().createDraft(1L, commandWithCoSupervisor(8L)));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
        verify(scenario.topics(), never()).saveAndFlush(any());
    }

    @Test
    void rejectsForgedDeanCoSupervisorEvenIfLegacyLecturerRoleIsPresent() {
        Scenario scenario = openScenario();
        DepartmentEntity department = department(31L, scenario.faculty());
        when(scenario.author().getDepartment()).thenReturn(department);
        UserAccountEntity dean = mock(UserAccountEntity.class);
        when(dean.getId()).thenReturn(8L);
        when(dean.getFaculty()).thenReturn(scenario.faculty());
        when(dean.getDepartment()).thenReturn(department);
        when(dean.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        when(dean.isEnabled()).thenReturn(true);
        when(scenario.accounts().findByIdForUpdate(8L)).thenReturn(Optional.of(dean));

        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> scenario.service().createDraft(1L, commandWithCoSupervisor(8L)));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, rejected.code());
        verify(scenario.topics(), never()).saveAndFlush(any());
    }

    @Test
    void coSupervisorInSameDepartmentIsStoredInSecondSlot() {
        Scenario scenario = openScenario();
        DepartmentEntity department = department(31L, scenario.faculty());
        when(scenario.author().getDepartment()).thenReturn(department);
        UserAccountEntity coSupervisor = mock(UserAccountEntity.class);
        when(coSupervisor.getId()).thenReturn(8L);
        when(coSupervisor.getFaculty()).thenReturn(scenario.faculty());
        when(coSupervisor.getDepartment()).thenReturn(department);
        when(coSupervisor.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(coSupervisor.isEnabled()).thenReturn(true);
        when(scenario.accounts().findByIdForUpdate(8L))
                .thenReturn(Optional.of(coSupervisor));
        when(scenario.topics().saveAndFlush(any(TopicEntity.class)))
                .thenAnswer(call -> call.getArgument(0));

        scenario.service().createDraft(1L, commandWithCoSupervisor(8L));

        org.mockito.ArgumentCaptor<TopicEntity> saved =
                org.mockito.ArgumentCaptor.forClass(TopicEntity.class);
        verify(scenario.topics()).saveAndFlush(saved.capture());
        assertEquals(scenario.author(), saved.getValue().getSupervisors().getFirst().getLecturer());
        assertEquals(1, saved.getValue().getSupervisors().getFirst().getSlot());
        assertEquals(coSupervisor, saved.getValue().getSupervisors().get(1).getLecturer());
        assertEquals(2, saved.getValue().getSupervisors().get(1).getSlot());
    }

    @Test
    void formContextListsOnlyLecturersInAuthorsDepartment() {
        Scenario scenario = openScenario();
        DepartmentEntity department = department(31L, scenario.faculty());
        when(scenario.author().getDepartment()).thenReturn(department);
        when(scenario.periods().findByIdAndFacultyIdAndDepartmentId(1L, 21L, 31L))
                .thenReturn(Optional.of(scenario.period()));
        when(scenario.period().getStudentEnd()).thenReturn(NOW.plusSeconds(1200));
        UserAccountEntity colleague = mock(UserAccountEntity.class);
        when(colleague.getId()).thenReturn(8L);
        when(colleague.getUsername()).thenReturn("colleague");
        when(colleague.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(scenario.accounts().findEnabledByFacultyAndDepartmentAndRole(
                21L, 31L, RoleCode.LECTURER))
                .thenReturn(List.of(scenario.author(), colleague));

        var context = scenario.service().formContext(1L);

        assertEquals(List.of(8L), context.coSupervisors().stream()
                .map(option -> option.id()).toList());
        verify(scenario.accounts(), never()).findEnabledByFacultyAndRole(21L, RoleCode.LECTURER);
    }

    @Test
    void formContextOmitsDeanReturnedByLegacyCandidateQuery() {
        Scenario scenario = openScenario();
        DepartmentEntity department = department(31L, scenario.faculty());
        when(scenario.author().getDepartment()).thenReturn(department);
        when(scenario.periods().findByIdAndFacultyIdAndDepartmentId(1L, 21L, 31L))
                .thenReturn(Optional.of(scenario.period()));
        when(scenario.period().getStudentEnd()).thenReturn(NOW.plusSeconds(1200));
        UserAccountEntity dean = mock(UserAccountEntity.class);
        when(dean.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        when(scenario.accounts().findEnabledByFacultyAndDepartmentAndRole(
                21L, 31L, RoleCode.LECTURER)).thenReturn(List.of(scenario.author(), dean));

        assertEquals(List.of(), scenario.service().formContext(1L).coSupervisors());
    }

    @Test
    void legacyDraftWithoutDepartmentCannotBeEdited() {
        Scenario scenario = openScenario();
        DepartmentEntity authorDepartment = department(31L, scenario.faculty());
        when(scenario.author().getDepartment()).thenReturn(authorDepartment);
        TopicEntity legacy = new TopicEntity(scenario.period(), scenario.author());
        when(scenario.topics().findPeriodIdByTopicId(5L)).thenReturn(Optional.of(1L));
        when(scenario.topics().findByIdForUpdate(5L)).thenReturn(Optional.of(legacy));
        when(scenario.authorization().requireTopicAuthor(7L, 21L))
                .thenReturn(new ActorIdentity(7L, Set.of(RoleCode.LECTURER), 21L));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().saveDraft(5L, 0L, completeCommand()));

        assertEquals(ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED, failure.code());
        verify(scenario.topics(), never()).flush();
    }

    @Test
    void topicOutsidePeriodsDepartmentCannotBeEdited() {
        Scenario scenario = openScenario();
        TopicEntity mismatched = new TopicEntity(scenario.period(), scenario.author(),
                department(32L, scenario.faculty()));
        when(scenario.topics().findPeriodIdByTopicId(5L)).thenReturn(Optional.of(1L));
        when(scenario.topics().findByIdForUpdate(5L)).thenReturn(Optional.of(mismatched));
        when(scenario.authorization().requireTopicAuthor(7L, 21L))
                .thenReturn(new ActorIdentity(7L, Set.of(RoleCode.LECTURER), 21L));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().saveDraft(5L, 0L, completeCommand()));

        assertEquals(ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED, failure.code());
        verify(scenario.topics(), never()).flush();
    }

    @Test
    void topicOutsidePeriodsDepartmentCannotBeSubmitted() {
        Scenario scenario = openScenario();
        TopicEntity mismatched = new TopicEntity(scenario.period(), scenario.author(),
                department(32L, scenario.faculty()));
        mismatched.updateDraft("Tên", "Mô tả", "Mục tiêu", "Yêu cầu", "Kết quả");
        mismatched.replaceSupervisors(List.of(scenario.author()));
        when(scenario.topics().findPeriodIdByTopicId(5L)).thenReturn(Optional.of(1L));
        when(scenario.topics().findByIdForUpdate(5L)).thenReturn(Optional.of(mismatched));
        when(scenario.authorization().requireTopicAuthor(7L, 21L))
                .thenReturn(new ActorIdentity(7L, Set.of(RoleCode.LECTURER), 21L));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().submit(5L, 0L));

        assertEquals(ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED, failure.code());
        assertEquals("DRAFT", mismatched.getStatus());
        verify(scenario.topics(), never()).flush();
    }

    @Test
    void periodListUsesPersistedLecturerDepartment() {
        Scenario scenario = openScenario();
        when(scenario.periods().findAllByFacultyIdAndDepartmentIdOrderByLecturerStartDesc(
                21L, 31L)).thenReturn(List.of(scenario.period()));
        when(scenario.period().getStudentEnd()).thenReturn(NOW.plusSeconds(1200));

        assertEquals(1, scenario.service().listPeriodsForAuthor().size());
        verify(scenario.periods()).findAllByFacultyIdAndDepartmentIdOrderByLecturerStartDesc(
                21L, 31L);
        verify(scenario.periods(), never()).findAllByFacultyIdOrderByLecturerStartDesc(21L);
    }

    @Test
    void directPeriodLookupOutsideDepartmentIsNotFound() {
        Scenario scenario = openScenario();

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().formContext(2L));

        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, failure.code());
        verify(scenario.periods()).findByIdAndFacultyIdAndDepartmentId(2L, 21L, 31L);
    }

    @Test
    void directDraftCreationForLegacyPeriodDoesNotPersist() {
        Scenario scenario = openScenario();
        when(scenario.period().getDepartment()).thenReturn(null);

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> scenario.service().createDraft(1L, completeCommand()));

        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, failure.code());
        verify(scenario.topics(), never()).saveAndFlush(any());
    }

    private static DepartmentEntity department(long id, FacultyEntity faculty) {
        DepartmentEntity department = mock(DepartmentEntity.class);
        when(department.getId()).thenReturn(id);
        when(department.getFaculty()).thenReturn(faculty);
        return department;
    }

    private static TopicDraftCommand commandWithCoSupervisor(long accountId) {
        return new TopicDraftCommand("Tên", "Mô tả", "Mục tiêu", "Yêu cầu", "Kết quả",
                List.of(accountId));
    }

    private static TopicDraftCommand completeCommand() {
        return new TopicDraftCommand("Tên", "Mô tả", "Mục tiêu", "Yêu cầu", "Kết quả", List.of());
    }

    private static Scenario openScenario() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        EntityManager entityManager = mock(EntityManager.class);
        TopicAuthorService service = new TopicAuthorService(periods, topics, registrations, accounts,
                authorization, entityManager, Clock.fixed(NOW, ZoneOffset.UTC));
        FacultyEntity faculty = mock(FacultyEntity.class);
        UserAccountEntity author = mock(UserAccountEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(faculty.getId()).thenReturn(21L);
        when(author.getId()).thenReturn(7L);
        when(author.getFaculty()).thenReturn(faculty);
        when(author.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(author.isEnabled()).thenReturn(true);
        when(author.getUsername()).thenReturn("lecturer");
        DepartmentEntity department = department(31L, faculty);
        when(author.getDepartment()).thenReturn(department);
        when(period.getId()).thenReturn(1L);
        when(period.getFaculty()).thenReturn(faculty);
        when(period.getDepartment()).thenReturn(department);
        when(period.getLecturerStart()).thenReturn(NOW.minusSeconds(600));
        when(period.getLecturerEnd()).thenReturn(NOW.plusSeconds(600));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        when(authorization.requireTopicAuthoringLecturer()).thenReturn(new ActorIdentity(
                7L, Set.of(RoleCode.LECTURER), 21L));
        when(entityManager.find(UserAccountEntity.class, 7L)).thenReturn(author);
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(author));
        return new Scenario(service, periods, topics, registrations, accounts, authorization, entityManager,
                faculty, author, period);
    }

    private record Scenario(TopicAuthorService service, RegistrationPeriodRepository periods,
            TopicRepository topics, RegistrationRepository registrations, UserAccountRepository accounts,
            AuthorizationPolicy authorization, EntityManager entityManager, FacultyEntity faculty,
            UserAccountEntity author, RegistrationPeriodEntity period) {
    }

    @Test
    void createDraftIsRejectedAfterLecturerWindowAndDoesNotPersist() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        EntityManager entityManager = mock(EntityManager.class);
        TopicAuthorService service = new TopicAuthorService(
                periods,
                topics,
                registrations,
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
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        EntityManager entityManager = mock(EntityManager.class);
        TopicAuthorService service = new TopicAuthorService(
                periods,
                topics,
                registrations,
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
        DepartmentEntity department = department(31L, faculty);
        when(author.getDepartment()).thenReturn(department);
        when(period.getId()).thenReturn(1L);
        when(period.getName()).thenReturn("Đợt cũ");
        when(period.getFaculty()).thenReturn(faculty);
        when(period.getDepartment()).thenReturn(department);
        when(period.getLecturerStart()).thenReturn(NOW.minusSeconds(600));
        when(period.getLecturerEnd()).thenReturn(NOW.minusSeconds(1));
        when(period.getStudentEnd()).thenReturn(NOW.plusSeconds(600));
        when(periods.findByIdAndFacultyIdAndDepartmentId(1L, 21L, 31L))
                .thenReturn(Optional.of(period));
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

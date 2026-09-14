package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import vn.edu.topicregistration.config.ReportStorageProperties;
import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.CouncilMemberRepository;
import vn.edu.topicregistration.repository.CouncilTopicRepository;
import vn.edu.topicregistration.repository.LocalReportFileRepository;
import vn.edu.topicregistration.repository.ProjectReportRepository;
import vn.edu.topicregistration.repository.RegistrationMemberRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.repository.ReportFileRepository;
import vn.edu.topicregistration.repository.ReportFileRepository.StoredReportFile;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.ReportService;
import vn.edu.topicregistration.service.ReportService.ReportUpload;

class ReportServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private static final byte[] PDF = "%PDF-1.7\ncontent".getBytes(StandardCharsets.US_ASCII);

    @Test
    void rejectsNonLeaderBeforeWritingAFile() throws Exception {
        Dependencies dependencies = dependencies();
        validRegistration(dependencies, false);
        ReportService service = dependencies.service();

        assertThrows(AccessDeniedException.class,
                () -> service.upload(11L, null, upload(PDF)));

        verify(dependencies.files(), never()).store(any(), anyLong());
        verify(dependencies.reports(), never()).saveAndFlush(any());
    }

    @Test
    void deletesNewFileWhenMetadataCannotBeCommitted() throws Exception {
        Dependencies dependencies = dependencies();
        validRegistration(dependencies, true);
        when(dependencies.files().store(any(), anyLong()))
                .thenReturn(new StoredReportFile("generated.pdf", PDF.length));
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(11L))
                .thenReturn(Optional.empty());
        when(dependencies.reports().saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate current report"));

        assertThrows(DataIntegrityViolationException.class,
                () -> dependencies.service().upload(11L, null, upload(PDF)));

        verify(dependencies.files()).delete("generated.pdf");
    }

    @Test
    void replacesOnlyTheMatchingCurrentVersionBeforeInsertingNewMetadata() throws Exception {
        Dependencies dependencies = dependencies();
        validRegistration(dependencies, true);
        vn.edu.topicregistration.entity.ProjectReportEntity current =
                mock(vn.edu.topicregistration.entity.ProjectReportEntity.class);
        when(current.getVersion()).thenReturn(4L);
        when(dependencies.reports().findCurrentByRegistrationIdForUpdate(11L))
                .thenReturn(Optional.of(current));
        when(dependencies.files().store(any(), anyLong()))
                .thenReturn(new StoredReportFile("generated.pdf", PDF.length));
        when(dependencies.reports().saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ReportService.ReportWorkspace result = dependencies.service().upload(
                11L,
                4L,
                upload(PDF));

        verify(current).markReplaced(NOW);
        verify(dependencies.reports()).flush();
        verify(dependencies.files(), never()).delete("generated.pdf");
        assertEquals("report.pdf", result.report().fileName());
    }

    @Test
    void directWorkspaceAccessDeniesAnUnrelatedStudent() {
        Dependencies dependencies = dependencies();
        DetailedFixture fixture = detailedRegistration(dependencies);
        when(dependencies.authorization().currentActor()).thenReturn(new ActorIdentity(
                99L, Set.of(RoleCode.STUDENT), 3L, 2L));
        when(dependencies.members().existsByRegistrationIdAndStudentId(11L, 99L))
                .thenReturn(false);
        when(dependencies.councilTopics().findByRegistrationId(11L))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().workspace(11L));
        verify(dependencies.reports(), never())
                .findByRegistrationIdAndReplacedAtIsNull(11L);
        assertEquals(11L, fixture.registration().getId());
    }

    @Test
    void localStorageUsesGeneratedKeysAndRejectsTraversal(@TempDir Path root) throws Exception {
        LocalReportFileRepository files = new LocalReportFileRepository(
                new ReportStorageProperties(root));

        StoredReportFile stored = files.store(new ByteArrayInputStream(PDF), (long) PDF.length);

        assertTrue(stored.storageKey().matches("[0-9a-f-]{36}\\.pdf"));
        assertArrayEquals(PDF, files.open(stored.storageKey()).readAllBytes());
        assertTrue(Files.isRegularFile(root.resolve(stored.storageKey())));
        assertThrows(IllegalArgumentException.class, () -> files.open("../outside.pdf"));
    }

    private static ReportUpload upload(byte[] bytes) {
        return new ReportUpload(
                "report.pdf",
                "application/pdf",
                bytes.length,
                () -> new ByteArrayInputStream(bytes));
    }

    private static void validRegistration(Dependencies dependencies, boolean leader) {
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        RegistrationEntity registration = mock(RegistrationEntity.class);
        UserAccountEntity submitter = mock(UserAccountEntity.class);
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        TopicEntity topic = mock(TopicEntity.class);
        when(period.getId()).thenReturn(7L);
        when(registration.getId()).thenReturn(11L);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getGroup()).thenReturn(group);
        when(registration.getTopic()).thenReturn(topic);
        when(registration.getStatus()).thenReturn(RegistrationStatus.ACCEPTED);
        when(group.getId()).thenReturn(21L);
        when(topic.getTitle()).thenReturn("Đề tài thử nghiệm");
        when(submitter.isEnabled()).thenReturn(true);
        when(dependencies.registrations().findPeriodIdByRegistrationId(11L))
                .thenReturn(Optional.of(7L));
        when(dependencies.periods().findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(dependencies.registrations().findByIdForUpdate(11L))
                .thenReturn(Optional.of(registration));
        when(dependencies.authorization().currentActor()).thenReturn(new ActorIdentity(
                31L, Set.of(RoleCode.STUDENT), 3L, 2L));
        when(dependencies.members()
                .existsByRegistrationIdAndStudentIdAndLeaderAtSubmissionTrue(11L, 31L))
                .thenReturn(leader);
        when(dependencies.councilTopics().findByRegistrationId(11L))
                .thenReturn(Optional.empty());
        when(dependencies.accounts().findById(31L)).thenReturn(Optional.of(submitter));
    }

    private static DetailedFixture detailedRegistration(Dependencies dependencies) {
        RegistrationEntity registration = mock(RegistrationEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        vn.edu.topicregistration.entity.FacultyEntity faculty =
                mock(vn.edu.topicregistration.entity.FacultyEntity.class);
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        TopicEntity topic = mock(TopicEntity.class);
        when(registration.getId()).thenReturn(11L);
        when(registration.getStatus()).thenReturn(RegistrationStatus.ACCEPTED);
        when(registration.getPeriod()).thenReturn(period);
        when(registration.getGroup()).thenReturn(group);
        when(registration.getTopic()).thenReturn(topic);
        when(period.getId()).thenReturn(7L);
        when(period.getFaculty()).thenReturn(faculty);
        when(faculty.getId()).thenReturn(2L);
        when(group.getId()).thenReturn(21L);
        when(topic.getTitle()).thenReturn("Đề tài thử nghiệm");
        when(topic.getSupervisors()).thenReturn(java.util.List.of());
        when(dependencies.registrations().findDetailedById(11L))
                .thenReturn(Optional.of(registration));
        return new DetailedFixture(registration);
    }

    private static Dependencies dependencies() {
        RegistrationPeriodRepository periods = mock(RegistrationPeriodRepository.class);
        RegistrationRepository registrations = mock(RegistrationRepository.class);
        RegistrationMemberRepository members = mock(RegistrationMemberRepository.class);
        ProjectReportRepository reports = mock(ProjectReportRepository.class);
        CouncilTopicRepository councilTopics = mock(CouncilTopicRepository.class);
        CouncilMemberRepository councilMembers = mock(CouncilMemberRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        ReportFileRepository files = mock(ReportFileRepository.class);
        return new Dependencies(
                periods,
                registrations,
                members,
                reports,
                councilTopics,
                councilMembers,
                accounts,
                authorization,
                files);
    }

    private record Dependencies(
            RegistrationPeriodRepository periods,
            RegistrationRepository registrations,
            RegistrationMemberRepository members,
            ProjectReportRepository reports,
            CouncilTopicRepository councilTopics,
            CouncilMemberRepository councilMembers,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            ReportFileRepository files) {
        ReportService service() {
            return new ReportService(
                    periods,
                    registrations,
                    members,
                    reports,
                    councilTopics,
                    councilMembers,
                    accounts,
                    authorization,
                    files,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    private record DetailedFixture(RegistrationEntity registration) {
    }
}

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import vn.edu.topicregistration.entity.AnnouncementEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.AnnouncementRepository;
import vn.edu.topicregistration.repository.FacultyRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.AnnouncementService;
import vn.edu.topicregistration.service.AnnouncementService.DraftCommand;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;

class AnnouncementServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-24T08:00:00Z");

    AnnouncementRepository announcements;
    UserAccountRepository accounts;
    FacultyRepository faculties;
    NotificationService notifications;
    AuthorizationPolicy authorization;
    AnnouncementService service;

    @BeforeEach
    void setUp() {
        announcements = mock(AnnouncementRepository.class);
        accounts = mock(UserAccountRepository.class);
        faculties = mock(FacultyRepository.class);
        notifications = mock(NotificationService.class);
        authorization = mock(AuthorizationPolicy.class);
        service = new AnnouncementService(
                announcements,
                accounts,
                faculties,
                notifications,
                authorization,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void adminCreatesTrimmedSchoolDraftWithServerOwnedSource() {
        UserAccountEntity admin = mock(UserAccountEntity.class);
        when(authorization.currentActor()).thenReturn(actor(7L, null, RoleCode.ADMIN));
        when(accounts.getReferenceById(7L)).thenReturn(admin);
        when(announcements.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new DraftCommand("  Nghỉ lễ  ", "  Nghỉ ngày thứ Hai.  "));

        assertEquals("Nghỉ lễ", result.title());
        assertEquals("Nghỉ ngày thứ Hai.", result.body());
        assertEquals("Nhà trường", result.sourceName());
        assertEquals(null, result.facultyId());
    }

    @Test
    void deanCreatesOnlyFacultyDraftUsingPersistedFacultyName() {
        FacultyEntity faculty = mock(FacultyEntity.class);
        UserAccountEntity dean = mock(UserAccountEntity.class);
        when(faculty.getId()).thenReturn(21L);
        when(faculty.getName()).thenReturn("Khoa Công nghệ thông tin");
        when(authorization.currentActor()).thenReturn(actor(8L, 21L, RoleCode.DEAN));
        when(faculties.findById(21L)).thenReturn(Optional.of(faculty));
        when(accounts.getReferenceById(8L)).thenReturn(dean);
        when(announcements.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(new DraftCommand("Lịch bảo vệ", "Sinh viên xem lịch."));

        assertEquals(21L, result.facultyId());
        assertEquals("Khoa Công nghệ thông tin", result.sourceName());
    }

    @Test
    void lecturerCannotCreateOfficialAnnouncement() {
        when(authorization.currentActor()).thenReturn(actor(9L, 21L, RoleCode.LECTURER));

        assertThrows(AccessDeniedException.class,
                () -> service.create(new DraftCommand("Thông báo", "Nội dung")));

        verify(announcements, never()).save(any());
    }

    @Test
    void invalidDraftIsRejectedBeforePersistence() {
        when(authorization.currentActor()).thenReturn(actor(7L, null, RoleCode.ADMIN));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> service.create(new DraftCommand("   ", "Nội dung")));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
        verify(announcements, never()).save(any());
    }

    @Test
    void updateRequiresMatchingScopeDraftAndVersion() {
        AnnouncementEntity schoolDraft = mock(AnnouncementEntity.class);
        when(schoolDraft.getFaculty()).thenReturn(null);
        when(schoolDraft.getVersion()).thenReturn(3L);
        when(schoolDraft.isPublished()).thenReturn(false);
        when(authorization.currentActor()).thenReturn(actor(7L, null, RoleCode.ADMIN));
        when(announcements.findByIdForUpdate(31L)).thenReturn(Optional.of(schoolDraft));
        when(announcements.saveAndFlush(schoolDraft)).thenReturn(schoolDraft);
        when(schoolDraft.getTitle()).thenReturn("Tiêu đề mới");
        when(schoolDraft.getBody()).thenReturn("Nội dung mới");
        when(schoolDraft.getSourceName()).thenReturn("Nhà trường");

        service.update(31L, 3L, new DraftCommand("Tiêu đề mới", "Nội dung mới"));

        verify(schoolDraft).revise("Tiêu đề mới", "Nội dung mới", NOW);
    }

    @Test
    void staleVersionAndPublishedDraftCannotBeChanged() {
        AnnouncementEntity draft = schoolAnnouncement(false, 4L);
        when(authorization.currentActor()).thenReturn(actor(7L, null, RoleCode.ADMIN));
        when(announcements.findByIdForUpdate(31L)).thenReturn(Optional.of(draft));

        ApplicationException stale = assertThrows(ApplicationException.class,
                () -> service.update(31L, 3L, new DraftCommand("Mới", "Nội dung mới")));
        assertEquals(ApplicationErrorCode.CONFLICT, stale.code());

        AnnouncementEntity published = schoolAnnouncement(true, 4L);
        when(announcements.findByIdForUpdate(32L)).thenReturn(Optional.of(published));
        ApplicationException immutable = assertThrows(ApplicationException.class,
                () -> service.update(32L, 4L, new DraftCommand("Mới", "Nội dung mới")));
        assertEquals(ApplicationErrorCode.CONFLICT, immutable.code());
    }

    @Test
    void publishSnapshotsEnabledScopeAndEmitsImmutableAnnouncementEvent() {
        AnnouncementEntity draft = schoolAnnouncement(false, 4L);
        UserAccountEntity admin = account(7L);
        UserAccountEntity firstRecipient = account(11L);
        UserAccountEntity secondRecipient = account(12L);
        when(draft.getId()).thenReturn(31L);
        when(draft.getTitle()).thenReturn("Thông báo học vụ");
        when(draft.getBody()).thenReturn("Nội dung chính thức");
        when(authorization.currentActor()).thenReturn(actor(7L, null, RoleCode.ADMIN));
        when(announcements.findByIdForUpdate(31L)).thenReturn(Optional.of(draft));
        when(accounts.getReferenceById(7L)).thenReturn(admin);
        when(accounts.findAllEnabled()).thenReturn(List.of(admin, firstRecipient, secondRecipient));
        when(announcements.saveAndFlush(draft)).thenReturn(draft);

        var result = service.publish(31L, 4L);

        assertEquals(2, result.recipientCount());
        verify(draft).publish(admin, NOW);
        ArgumentCaptor<EmissionCommand> command = ArgumentCaptor.forClass(EmissionCommand.class);
        verify(notifications).emit(command.capture());
        assertEquals(NotificationEventType.ANNOUNCEMENT_PUBLISHED, command.getValue().eventType());
        assertEquals(List.of(11L, 12L), command.getValue().recipientIds().stream().sorted().toList());
        assertEquals(NotificationTargetType.ANNOUNCEMENT, command.getValue().targetType());
        assertEquals(31L, command.getValue().targetId());
    }

    private static AnnouncementEntity schoolAnnouncement(boolean published, long version) {
        AnnouncementEntity announcement = mock(AnnouncementEntity.class);
        when(announcement.getFaculty()).thenReturn(null);
        when(announcement.isPublished()).thenReturn(published);
        when(announcement.getVersion()).thenReturn(version);
        return announcement;
    }

    private static UserAccountEntity account(long id) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(id);
        return account;
    }

    private static ActorIdentity actor(long id, Long facultyId, RoleCode... roles) {
        return new ActorIdentity(id, Set.of(roles), null, facultyId);
    }
}

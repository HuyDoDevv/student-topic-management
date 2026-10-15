package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import vn.edu.topicregistration.entity.NotificationCategory;
import vn.edu.topicregistration.entity.NotificationEventEntity;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserNotificationEntity;
import vn.edu.topicregistration.entity.AnnouncementEntity;
import vn.edu.topicregistration.entity.FinalResultEntity;
import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.entity.GroupInvitationEntity;
import vn.edu.topicregistration.entity.GroupInvitationStatus;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.AnnouncementRepository;
import vn.edu.topicregistration.repository.FinalResultRepository;
import vn.edu.topicregistration.repository.GroupInvitationRepository;
import vn.edu.topicregistration.repository.NotificationEventRepository;
import vn.edu.topicregistration.repository.StudentGroupRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserNotificationRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.NotificationEventKey;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;
import vn.edu.topicregistration.service.NotificationTargetResolver;
import vn.edu.topicregistration.service.RegistrationService;
import vn.edu.topicregistration.service.ScoringService;
import vn.edu.topicregistration.service.TopicAuthorService;

class NotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-24T02:00:00Z");

    @Test
    void eventKeysAreStableForEveryApprovedTransition() {
        assertEquals("ANNOUNCEMENT:7:PUBLISHED", key(NotificationEventType.ANNOUNCEMENT_PUBLISHED));
        assertEquals("GROUP_INVITATION:7:CREATED", key(NotificationEventType.GROUP_INVITATION_CREATED));
        assertEquals("GROUP_INVITATION:7:ACCEPTED", key(NotificationEventType.GROUP_INVITATION_ACCEPTED));
        assertEquals("GROUP_INVITATION:7:REJECTED", key(NotificationEventType.GROUP_INVITATION_REJECTED));
        assertEquals("TOPIC:7:APPROVED", key(NotificationEventType.TOPIC_PROPOSAL_APPROVED));
        assertEquals("TOPIC:7:REJECTED", key(NotificationEventType.TOPIC_PROPOSAL_REJECTED));
        assertEquals("REGISTRATION:7:ACCEPTED", key(NotificationEventType.REGISTRATION_ACCEPTED));
        assertEquals("REGISTRATION:7:REJECTED", key(NotificationEventType.REGISTRATION_REJECTED));
        assertEquals("REGISTRATION:7:AUTO_REJECTED", key(NotificationEventType.REGISTRATION_AUTO_REJECTED));
        assertEquals("DIRECT_GRADING:7:ASSIGNED", key(NotificationEventType.DIRECT_GRADER_ASSIGNED));
        assertEquals("COUNCIL_MEMBER:7:ASSIGNED", key(NotificationEventType.COUNCIL_MEMBER_ASSIGNED));
        assertEquals("RESULT:7:PUBLISHED", key(NotificationEventType.RESULT_PUBLISHED));
        assertThrows(IllegalArgumentException.class,
                () -> NotificationEventKey.of(NotificationEventType.RESULT_PUBLISHED, 0));
    }

    @Test
    void emitsOneDeliveryPerDistinctRecipientAndExcludesActor() {
        Dependencies dependencies = dependencies();
        UserAccountEntity recipient2 = account(2L);
        UserAccountEntity recipient3 = account(3L);
        UserAccountEntity actor = account(1L);
        when(dependencies.accounts().findAllById(Set.of(2L, 3L)))
                .thenReturn(List.of(recipient2, recipient3));
        when(dependencies.accounts().getReferenceById(1L)).thenReturn(actor);
        when(dependencies.events().saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dependencies.notifications().saveAllAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = dependencies.service().emit(new EmissionCommand(
                NotificationEventType.REGISTRATION_ACCEPTED,
                41L,
                1L,
                List.of(1L, 2L, 2L, 3L),
                "  Đăng ký đã được chấp nhận  ",
                "  Nhóm của bạn đã được chấp nhận.  ",
                null,
                NotificationTargetType.REGISTRATION,
                41L));

        assertTrue(result.created());
        assertEquals(2, result.recipientCount());
        assertEquals("REGISTRATION:41:ACCEPTED", result.eventKey());
        ArgumentCaptor<NotificationEventEntity> event =
                ArgumentCaptor.forClass(NotificationEventEntity.class);
        verify(dependencies.events()).saveAndFlush(event.capture());
        assertEquals("Đăng ký đã được chấp nhận", event.getValue().getTitle());
        assertEquals(NotificationCategory.ACADEMIC_EVENT, event.getValue().getCategory());
        assertEquals(NOW, event.getValue().getCreatedAt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UserNotificationEntity>> deliveries =
                ArgumentCaptor.forClass(Iterable.class);
        verify(dependencies.notifications()).saveAllAndFlush(deliveries.capture());
        List<Long> recipientIds = new java.util.ArrayList<>();
        deliveries.getValue().forEach(delivery -> recipientIds.add(delivery.getRecipient().getId()));
        assertEquals(Set.of(2L, 3L), Set.copyOf(recipientIds));
    }

    @Test
    void repeatedCommittedEventIsIdempotentAndDoesNotAddRecipients() {
        Dependencies dependencies = dependencies();
        NotificationEventEntity existing = mock(NotificationEventEntity.class);
        when(existing.getId()).thenReturn(88L);
        when(dependencies.events().findByEventKey("RESULT:9:PUBLISHED"))
                .thenReturn(Optional.of(existing));

        var result = dependencies.service().emit(new EmissionCommand(
                NotificationEventType.RESULT_PUBLISHED,
                9L,
                1L,
                List.of(2L),
                "Kết quả đã công bố",
                "Bạn có thể xem kết quả.",
                null,
                NotificationTargetType.RESULT,
                20L));

        assertFalse(result.created());
        assertEquals(88L, result.eventId());
        assertEquals(0, result.recipientCount());
        verify(dependencies.events(), never()).saveAndFlush(any());
        verify(dependencies.notifications(), never()).saveAllAndFlush(any());
    }

    @Test
    void rejectsMissingRecipientsAndInvalidTargetsBeforeWriting() {
        Dependencies dependencies = dependencies();
        UserAccountEntity onlyRecipient = account(2L);
        when(dependencies.accounts().findAllById(Set.of(2L, 3L)))
                .thenReturn(List.of(onlyRecipient));

        assertThrows(IllegalArgumentException.class, () -> dependencies.service().emit(
                academicCommand(List.of(2L, 3L), NotificationTargetType.TOPIC, 4L)));
        assertThrows(IllegalArgumentException.class, () -> dependencies.service().emit(
                academicCommand(List.of(2L), NotificationTargetType.NONE, 4L)));
        assertThrows(IllegalArgumentException.class, () -> dependencies.service().emit(
                academicCommand(List.of(1L), NotificationTargetType.TOPIC, 4L)));

        verify(dependencies.events(), never()).saveAndFlush(any());
        verify(dependencies.notifications(), never()).saveAllAndFlush(any());
    }

    @Test
    void queriesOnlyCurrentActorsInboxAndMapsUnreadItems() {
        Dependencies dependencies = dependencies();
        when(dependencies.authorization().currentActor()).thenReturn(
                new ActorIdentity(9L, Set.of(RoleCode.STUDENT), 2L));
        NotificationEventEntity event = mock(NotificationEventEntity.class);
        when(event.getTitle()).thenReturn("Kết quả đã công bố");
        when(event.getBody()).thenReturn("Xem kết quả của bạn.");
        when(event.getCategory()).thenReturn(NotificationCategory.ACADEMIC_EVENT);
        when(event.getEventType()).thenReturn(NotificationEventType.RESULT_PUBLISHED);
        when(event.getTargetType()).thenReturn(NotificationTargetType.RESULT);
        when(event.getTargetId()).thenReturn(41L);
        UserNotificationEntity delivery = mock(UserNotificationEntity.class);
        when(delivery.getId()).thenReturn(15L);
        when(delivery.getEvent()).thenReturn(event);
        when(delivery.getDeliveredAt()).thenReturn(NOW);
        when(dependencies.notifications().findByRecipientId(
                org.mockito.ArgumentMatchers.eq(9L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(delivery)));
        when(dependencies.notifications().findInbox(
                org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.isNull(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(delivery)));
        when(dependencies.notifications().countByRecipientIdAndReadAtIsNull(9L)).thenReturn(1L);

        var header = dependencies.service().headerForCurrentActor();
        var inbox = dependencies.service().inboxForCurrentActor(0);

        assertEquals(1, header.unreadCount());
        assertEquals(1, header.items().size());
        assertTrue(header.items().getFirst().unread());
        assertEquals(15L, inbox.getContent().getFirst().id());
        verify(dependencies.notifications()).countByRecipientIdAndReadAtIsNull(9L);
        assertThrows(IllegalArgumentException.class,
                () -> dependencies.service().inboxForCurrentActor(-1));
    }

    @Test
    void resolvesOnlyVisibleTargetsForTheirRecipients() {
        ResolverDependencies dependencies = resolverDependencies();
        ActorIdentity student = new ActorIdentity(9L, Set.of(RoleCode.STUDENT), 2L);
        ActorIdentity lecturer = new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 2L);

        AnnouncementEntity announcement = mock(AnnouncementEntity.class);
        when(announcement.getId()).thenReturn(4L);
        when(announcement.isPublished()).thenReturn(true);
        when(dependencies.announcements().findById(4L)).thenReturn(Optional.of(announcement));
        NotificationEventEntity announcementEvent = event(NotificationTargetType.ANNOUNCEMENT, 4L);
        when(announcementEvent.getAnnouncement()).thenReturn(announcement);
        assertEquals("/announcements/4", dependencies.resolver().resolve(announcementEvent, student));

        GroupInvitationEntity invitation = mock(GroupInvitationEntity.class);
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        FacultyEntity faculty = mock(FacultyEntity.class);
        StudentGroupEntity group = mock(StudentGroupEntity.class);
        UserAccountEntity invitee = account(9L);
        when(invitation.getStudent()).thenReturn(invitee);
        when(invitation.getPeriod()).thenReturn(period);
        when(invitation.getGroup()).thenReturn(group);
        when(invitation.getStatus()).thenReturn(GroupInvitationStatus.PENDING);
        when(period.getFaculty()).thenReturn(faculty);
        when(faculty.getId()).thenReturn(2L);
        when(period.getStudentStart()).thenReturn(NOW.minusSeconds(60));
        when(period.getStudentEnd()).thenReturn(NOW.plusSeconds(60));
        when(group.isActive()).thenReturn(true);
        when(dependencies.invitations().findById(4L)).thenReturn(Optional.of(invitation));
        assertEquals("/group-invitations", dependencies.resolver().resolve(
                event(NotificationTargetType.GROUP_INVITATION, 4L), student));
        ActorIdentity otherFacultyStudent = new ActorIdentity(
                9L, Set.of(RoleCode.STUDENT), 3L);
        assertEquals(NotificationTargetResolver.UNAVAILABLE, dependencies.resolver().resolve(
                event(NotificationTargetType.GROUP_INVITATION, 4L), otherFacultyStudent));

        when(group.getPeriod()).thenReturn(period);
        when(dependencies.groups().findByIdVisibleToStudent(4L, 9L))
                .thenReturn(Optional.of(group));
        assertEquals("/groups/4", dependencies.resolver().resolve(
                event(NotificationTargetType.GROUP, 4L), student));
        assertEquals(NotificationTargetResolver.UNAVAILABLE, dependencies.resolver().resolve(
                event(NotificationTargetType.GROUP, 4L), otherFacultyStudent));

        TopicAuthorService.TopicView topic = mock(TopicAuthorService.TopicView.class);
        when(topic.periodId()).thenReturn(7L);
        when(dependencies.topics().getOwn(4L)).thenReturn(topic);
        assertEquals("/topics/4", dependencies.resolver().resolve(
                event(NotificationTargetType.TOPIC, 4L), lecturer));

        assertEquals("/registrations/4", dependencies.resolver().resolve(
                event(NotificationTargetType.REGISTRATION, 4L), student));
        verify(dependencies.registrations()).getVisible(4L);

        ScoringService.ScoringAssignmentView direct = new ScoringService.ScoringAssignmentView(
                4L, "Chấm trực tiếp", 8L, 3L, "Đề tài", NOW, "Trực tiếp", true, 0L, true);
        when(dependencies.scoring().listForCurrentLecturer()).thenReturn(List.of(direct));
        assertEquals("/lecturer/scoring/4/8", dependencies.resolver().resolve(
                event(NotificationTargetType.DIRECT_GRADING, 4L), lecturer));
        verify(dependencies.scoring()).workspace(4L, 8L);
        assertEquals(NotificationTargetResolver.UNAVAILABLE, dependencies.resolver().resolve(
                event(NotificationTargetType.COUNCIL, 4L), lecturer));

        ScoringService.ScoringAssignmentView council = new ScoringService.ScoringAssignmentView(
                5L, "Hội đồng", 9L, 3L, "Đề tài", NOW, "Phòng 1", true, 0L, false);
        when(dependencies.scoring().listForCurrentLecturer()).thenReturn(List.of(council));
        assertEquals("/lecturer/scoring", dependencies.resolver().resolve(
                event(NotificationTargetType.COUNCIL, 5L), lecturer));

        when(dependencies.results().findVisibleToStudent(4L, 9L, FinalResultStatus.PUBLISHED))
                .thenReturn(Optional.of(mock(FinalResultEntity.class)));
        assertEquals("/student/results/4", dependencies.resolver().resolve(
                event(NotificationTargetType.RESULT, 4L), student));
        assertEquals("/notifications", dependencies.resolver().resolve(
                event(NotificationTargetType.NONE, null), student));

        when(announcement.isPublished()).thenReturn(false);
        assertEquals(NotificationTargetResolver.UNAVAILABLE,
                dependencies.resolver().resolve(announcementEvent, student));
        when(invitation.getStatus()).thenReturn(GroupInvitationStatus.REJECTED);
        assertEquals(NotificationTargetResolver.UNAVAILABLE, dependencies.resolver().resolve(
                event(NotificationTargetType.GROUP_INVITATION, 4L), student));
    }

    @Test
    void unavailableTargetsFallBackWithoutGrantingAccess() {
        ResolverDependencies dependencies = resolverDependencies();
        ActorIdentity student = new ActorIdentity(9L, Set.of(RoleCode.STUDENT), 2L);
        ActorIdentity lecturer = new ActorIdentity(11L, Set.of(RoleCode.LECTURER), 2L);
        when(dependencies.topics().getOwn(77L))
                .thenThrow(new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        assertEquals(NotificationTargetResolver.UNAVAILABLE, dependencies.resolver().resolve(
                event(NotificationTargetType.TOPIC, 77L), lecturer));
        assertEquals(NotificationTargetResolver.UNAVAILABLE, dependencies.resolver().resolve(
                event(NotificationTargetType.DIRECT_GRADING, 4L), lecturer));
        assertEquals(NotificationTargetResolver.UNAVAILABLE, dependencies.resolver().resolve(
                event(NotificationTargetType.RESULT, 4L), student));
        assertEquals(NotificationTargetResolver.UNAVAILABLE, dependencies.resolver().resolve(
                event(NotificationTargetType.NONE, 4L), student));

        when(dependencies.registrations().getVisible(4L))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Outside faculty"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> dependencies.resolver().resolve(
                        event(NotificationTargetType.REGISTRATION, 4L), student));
    }

    @Test
    void openMarksOnlyOwnedNotificationReadAfterResolvingFallback() {
        Dependencies dependencies = dependencies();
        ActorIdentity actor = new ActorIdentity(9L, Set.of(RoleCode.STUDENT), 2L);
        when(dependencies.authorization().currentActor()).thenReturn(actor);
        NotificationEventEntity event = event(NotificationTargetType.TOPIC, 77L);
        UserNotificationEntity own = mock(UserNotificationEntity.class);
        when(own.getEvent()).thenReturn(event);
        when(dependencies.notifications().findByIdAndRecipientId(15L, 9L))
                .thenReturn(Optional.of(own));
        when(dependencies.resolver().resolve(event, actor))
                .thenReturn(NotificationTargetResolver.UNAVAILABLE);

        assertEquals(NotificationTargetResolver.UNAVAILABLE,
                dependencies.service().openForCurrentActor(15L));
        verify(dependencies.notifications()).markRead(15L, 9L, NOW);

        ApplicationException missing = assertThrows(ApplicationException.class,
                () -> dependencies.service().openForCurrentActor(16L));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, missing.code());
        verify(dependencies.notifications(), never()).markRead(16L, 9L, NOW);
    }

    private static NotificationEventEntity event(NotificationTargetType type, Long targetId) {
        NotificationEventEntity event = mock(NotificationEventEntity.class);
        when(event.getTargetType()).thenReturn(type);
        when(event.getTargetId()).thenReturn(targetId);
        return event;
    }

    private static ResolverDependencies resolverDependencies() {
        AnnouncementRepository announcements = mock(AnnouncementRepository.class);
        GroupInvitationRepository invitations = mock(GroupInvitationRepository.class);
        StudentGroupRepository groups = mock(StudentGroupRepository.class);
        TopicAuthorService topics = mock(TopicAuthorService.class);
        RegistrationService registrations = mock(RegistrationService.class);
        ScoringService scoring = mock(ScoringService.class);
        FinalResultRepository results = mock(FinalResultRepository.class);
        NotificationTargetResolver resolver = new NotificationTargetResolver(
                announcements, invitations, groups, topics, registrations, scoring,
                results, Clock.fixed(NOW, ZoneOffset.UTC));
        return new ResolverDependencies(
                resolver, announcements, invitations, groups, topics, registrations,
                scoring, results);
    }

    private record ResolverDependencies(
            NotificationTargetResolver resolver,
            AnnouncementRepository announcements,
            GroupInvitationRepository invitations,
            StudentGroupRepository groups,
            TopicAuthorService topics,
            RegistrationService registrations,
            ScoringService scoring,
            FinalResultRepository results) {
    }

    private static String key(NotificationEventType type) {
        return NotificationEventKey.of(type, 7L);
    }

    private static EmissionCommand academicCommand(
            List<Long> recipients, NotificationTargetType targetType, Long targetId) {
        return new EmissionCommand(
                NotificationEventType.TOPIC_PROPOSAL_APPROVED,
                4L,
                1L,
                recipients,
                "Đề tài được duyệt",
                "Đề tài của bạn đã được duyệt.",
                null,
                targetType,
                targetId);
    }

    private static UserAccountEntity account(long id) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(id);
        return account;
    }

    private static Dependencies dependencies() {
        NotificationEventRepository events = mock(NotificationEventRepository.class);
        UserNotificationRepository notifications = mock(UserNotificationRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AnnouncementRepository announcements = mock(AnnouncementRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        NotificationTargetResolver resolver = mock(NotificationTargetResolver.class);
        NotificationService service = new NotificationService(
                events,
                notifications,
                accounts,
                announcements,
                authorization,
                resolver,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Dependencies(service, events, notifications, accounts, authorization, resolver);
    }

    private record Dependencies(
            NotificationService service,
            NotificationEventRepository events,
            UserNotificationRepository notifications,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            NotificationTargetResolver resolver) {
    }
}

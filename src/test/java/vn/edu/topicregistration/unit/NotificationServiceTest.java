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
import vn.edu.topicregistration.repository.AnnouncementRepository;
import vn.edu.topicregistration.repository.NotificationEventRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserNotificationRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.NotificationEventKey;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;
import vn.edu.topicregistration.service.NotificationTargetResolver;

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
                new ActorIdentity(9L, Set.of(RoleCode.STUDENT), 3L, 2L));
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
    void resolvesOnlyClosedInternalTargets() {
        NotificationTargetResolver resolver = new NotificationTargetResolver();

        assertEquals("/announcements/4", resolver.resolve(NotificationTargetType.ANNOUNCEMENT, 4L));
        assertEquals("/group-invitations", resolver.resolve(NotificationTargetType.GROUP_INVITATION, 4L));
        assertEquals("/groups/4", resolver.resolve(NotificationTargetType.GROUP, 4L));
        assertEquals("/topics/4", resolver.resolve(NotificationTargetType.TOPIC, 4L));
        assertEquals("/registrations/4", resolver.resolve(NotificationTargetType.REGISTRATION, 4L));
        assertEquals("/lecturer/direct-grading", resolver.resolve(NotificationTargetType.DIRECT_GRADING, 4L));
        assertEquals("/lecturer/scoring", resolver.resolve(NotificationTargetType.COUNCIL, 4L));
        assertEquals("/student/results/4", resolver.resolve(NotificationTargetType.RESULT, 4L));
        assertEquals("/notifications", resolver.resolve(NotificationTargetType.NONE, null));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(NotificationTargetType.NONE, 4L));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(NotificationTargetType.TOPIC, null));
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
        NotificationService service = new NotificationService(
                events,
                notifications,
                accounts,
                announcements,
                authorization,
                new NotificationTargetResolver(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Dependencies(service, events, notifications, accounts, authorization);
    }

    private record Dependencies(
            NotificationService service,
            NotificationEventRepository events,
            UserNotificationRepository notifications,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization) {
    }
}

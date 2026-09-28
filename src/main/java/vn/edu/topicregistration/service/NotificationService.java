package vn.edu.topicregistration.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.AnnouncementEntity;
import vn.edu.topicregistration.entity.NotificationCategory;
import vn.edu.topicregistration.entity.NotificationEventEntity;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserNotificationEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.AnnouncementRepository;
import vn.edu.topicregistration.repository.NotificationEventRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserNotificationRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;

@Service
@Profile("!test")
public class NotificationService {
    public static final int HEADER_LIMIT = 5;
    public static final int INBOX_PAGE_SIZE = 20;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 5_000;
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("deliveredAt"), Sort.Order.desc("id"));

    private final NotificationEventRepository events;
    private final UserNotificationRepository notifications;
    private final UserAccountRepository accounts;
    private final AnnouncementRepository announcements;
    private final AuthorizationPolicy authorization;
    private final NotificationTargetResolver targetResolver;
    private final Clock clock;

    public NotificationService(
            NotificationEventRepository events,
            UserNotificationRepository notifications,
            UserAccountRepository accounts,
            AnnouncementRepository announcements,
            AuthorizationPolicy authorization,
            NotificationTargetResolver targetResolver,
            Clock clock) {
        this.events = events;
        this.notifications = notifications;
        this.accounts = accounts;
        this.announcements = announcements;
        this.authorization = authorization;
        this.targetResolver = targetResolver;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EmissionResult emit(EmissionCommand command) {
        Objects.requireNonNull(command, "command");
        String eventKey = NotificationEventKey.of(command.eventType(), command.sourceId());
        NotificationEventEntity existing = events.findByEventKey(eventKey).orElse(null);
        if (existing != null) {
            return new EmissionResult(existing.getId(), eventKey, 0, false);
        }

        String title = normalized(command.title(), MAX_TITLE_LENGTH, "title");
        String body = normalized(command.body(), MAX_BODY_LENGTH, "body");
        validateTarget(command.targetType(), command.targetId());
        validateAnnouncement(command.eventType(), command.announcementId());

        Set<Long> recipientIds = normalizedRecipients(command.recipientIds(), command.actorId());
        List<UserAccountEntity> recipients = accounts.findAllById(recipientIds);
        if (recipients.size() != recipientIds.size()
                || recipients.stream().map(UserAccountEntity::getId).distinct().count()
                != recipientIds.size()) {
            throw new IllegalArgumentException("Every notification recipient must exist");
        }

        UserAccountEntity actor = command.actorId() == null
                ? null
                : accounts.getReferenceById(command.actorId());
        AnnouncementEntity announcement = command.announcementId() == null
                ? null
                : announcements.getReferenceById(command.announcementId());
        Instant now = clock.instant();
        NotificationEventEntity event = events.saveAndFlush(new NotificationEventEntity(
                eventKey,
                command.eventType(),
                title,
                body,
                actor,
                announcement,
                command.targetType(),
                command.targetId(),
                now));
        List<UserNotificationEntity> deliveries = recipients.stream()
                .map(recipient -> new UserNotificationEntity(event, recipient, now))
                .toList();
        notifications.saveAllAndFlush(deliveries);
        return new EmissionResult(event.getId(), eventKey, deliveries.size(), true);
    }

    @Transactional(readOnly = true)
    public HeaderView headerForCurrentActor() {
        long recipientId = currentAccountId();
        Page<UserNotificationEntity> page = notifications.findByRecipientId(
                recipientId, PageRequest.of(0, HEADER_LIMIT, NEWEST_FIRST));
        return new HeaderView(
                notifications.countByRecipientIdAndReadAtIsNull(recipientId),
                page.getContent().stream().map(NotificationService::toItem).toList());
    }

    @Transactional(readOnly = true)
    public Page<NotificationItem> inboxForCurrentActor(int pageNumber) {
        return inboxForCurrentActor(InboxStatus.ALL, InboxCategory.ALL, pageNumber);
    }

    @Transactional(readOnly = true)
    public Page<NotificationItem> inboxForCurrentActor(
            InboxStatus status,
            InboxCategory category,
            int pageNumber) {
        if (status == null || category == null) {
            throw new IllegalArgumentException("Notification filters are required");
        }
        if (pageNumber < 0) {
            throw new IllegalArgumentException("Notification page must not be negative");
        }
        Pageable pageable = PageRequest.of(pageNumber, INBOX_PAGE_SIZE, NEWEST_FIRST);
        return notifications.findInbox(
                        currentAccountId(),
                        status == InboxStatus.UNREAD,
                        category.notificationCategory(),
                        pageable)
                .map(NotificationService::toItem);
    }

    @Transactional
    public void markAsReadForCurrentActor(long notificationId) {
        long recipientId = currentAccountId();
        requireOwned(notificationId, recipientId);
        notifications.markRead(notificationId, recipientId, clock.instant());
    }

    @Transactional
    public int markAllAsReadForCurrentActor() {
        long recipientId = currentAccountId();
        Instant cutoff = clock.instant();
        return notifications.markAllReadThrough(recipientId, cutoff, cutoff);
    }

    @Transactional
    public String openForCurrentActor(long notificationId) {
        long recipientId = currentAccountId();
        UserNotificationEntity notification = requireOwned(notificationId, recipientId);
        String target = targetResolver.resolve(
                notification.getEvent().getTargetType(),
                notification.getEvent().getTargetId());
        notifications.markRead(notificationId, recipientId, clock.instant());
        return target;
    }

    private UserNotificationEntity requireOwned(long notificationId, long recipientId) {
        if (notificationId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        return notifications.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private long currentAccountId() {
        Long accountId = authorization.currentActor().accountId();
        if (accountId == null || accountId <= 0) {
            throw new IllegalStateException("Authenticated notification account is missing");
        }
        return accountId;
    }

    private static NotificationItem toItem(UserNotificationEntity notification) {
        NotificationEventEntity event = notification.getEvent();
        return new NotificationItem(
                notification.getId(),
                event.getTitle(),
                event.getBody(),
                event.getCategory(),
                event.getEventType(),
                event.getTargetType(),
                event.getTargetId(),
                notification.getDeliveredAt(),
                notification.getReadAt());
    }

    private static Set<Long> normalizedRecipients(Collection<Long> recipientIds, Long actorId) {
        if (recipientIds == null) {
            throw new IllegalArgumentException("Notification recipients are required");
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long recipientId : recipientIds) {
            if (recipientId == null || recipientId <= 0) {
                throw new IllegalArgumentException("Notification recipient id must be positive");
            }
            if (!recipientId.equals(actorId)) {
                normalized.add(recipientId);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Notification needs at least one recipient after actor exclusion");
        }
        return Set.copyOf(normalized);
    }

    private static String normalized(String value, int maxLength, String field) {
        String normalized = value == null ? null : value.strip();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("Invalid notification " + field);
        }
        return normalized;
    }

    private static void validateTarget(NotificationTargetType targetType, Long targetId) {
        if (targetType == null) {
            throw new IllegalArgumentException("Notification target type is required");
        }
        if (targetType == NotificationTargetType.NONE && targetId != null) {
            throw new IllegalArgumentException("NONE notification target cannot have an id");
        }
        if (targetType != NotificationTargetType.NONE && (targetId == null || targetId <= 0)) {
            throw new IllegalArgumentException("Notification target id must be positive");
        }
    }

    private static void validateAnnouncement(NotificationEventType eventType, Long announcementId) {
        boolean announcementEvent = eventType == NotificationEventType.ANNOUNCEMENT_PUBLISHED;
        if (announcementEvent != (announcementId != null)) {
            throw new IllegalArgumentException("Announcement reference does not match event type");
        }
        if (announcementId != null && announcementId <= 0) {
            throw new IllegalArgumentException("Announcement id must be positive");
        }
    }

    public record EmissionCommand(
            NotificationEventType eventType,
            long sourceId,
            Long actorId,
            Collection<Long> recipientIds,
            String title,
            String body,
            Long announcementId,
            NotificationTargetType targetType,
            Long targetId) {
        public EmissionCommand {
            recipientIds = recipientIds == null ? null : List.copyOf(recipientIds);
        }
    }

    public record EmissionResult(
            Long eventId,
            String eventKey,
            int recipientCount,
            boolean created) {
    }

    public record NotificationItem(
            Long id,
            String title,
            String body,
            NotificationCategory category,
            NotificationEventType eventType,
            NotificationTargetType targetType,
            Long targetId,
            Instant deliveredAt,
            Instant readAt) {
        public boolean unread() {
            return readAt == null;
        }
    }

    public record HeaderView(long unreadCount, List<NotificationItem> items) {
        public HeaderView {
            items = List.copyOf(items);
        }
    }

    public enum InboxStatus {
        ALL,
        UNREAD
    }

    public enum InboxCategory {
        ALL(null),
        ANNOUNCEMENT(NotificationCategory.ANNOUNCEMENT),
        ACADEMIC_EVENT(NotificationCategory.ACADEMIC_EVENT);

        private final NotificationCategory notificationCategory;

        InboxCategory(NotificationCategory notificationCategory) {
            this.notificationCategory = notificationCategory;
        }

        NotificationCategory notificationCategory() {
            return notificationCategory;
        }
    }
}

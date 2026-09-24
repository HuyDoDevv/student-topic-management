package vn.edu.topicregistration.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.Announcement;
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
import vn.edu.topicregistration.repository.UserNotificationRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;

@Service
@Profile("!preview & !test")
public class AnnouncementService {
    public static final int PAGE_SIZE = 20;
    private static final String SCHOOL_SOURCE = "Nhà trường";
    private static final Sort NEWEST_UPDATED = Sort.by(
            Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));

    private final AnnouncementRepository announcements;
    private final UserAccountRepository accounts;
    private final FacultyRepository faculties;
    private final NotificationService notifications;
    private final UserNotificationRepository userNotifications;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public AnnouncementService(
            AnnouncementRepository announcements,
            UserAccountRepository accounts,
            FacultyRepository faculties,
            NotificationService notifications,
            UserNotificationRepository userNotifications,
            AuthorizationPolicy authorization,
            Clock clock) {
        this.announcements = announcements;
        this.accounts = accounts;
        this.faculties = faculties;
        this.notifications = notifications;
        this.userNotifications = userNotifications;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional
    public AnnouncementView create(DraftCommand command) {
        Announcement content = validated(command);
        ManagerScope scope = resolveManagerScope();
        UserAccountEntity creator = accounts.getReferenceById(scope.actor().accountId());
        Instant now = clock.instant();
        AnnouncementEntity draft = new AnnouncementEntity(
                content.title(),
                content.body(),
                scope.sourceName(),
                scope.faculty(),
                creator,
                now);
        return toView(announcements.saveAndFlush(draft));
    }

    @Transactional(readOnly = true)
    public ManagerScopeView currentManagerScope() {
        ManagerScope scope = resolveManagerScope();
        return new ManagerScopeView(
                scope.sourceName(),
                scope.faculty() == null ? null : scope.faculty().getId());
    }

    @Transactional(readOnly = true)
    public Page<AnnouncementView> listForCurrentManager(
            AnnouncementStatusFilter status,
            int pageNumber) {
        if (status == null || pageNumber < 0) {
            throw new ApplicationException(ApplicationErrorCode.BAD_REQUEST);
        }
        ManagerScope scope = resolveManagerScope();
        PageRequest pageable = PageRequest.of(pageNumber, PAGE_SIZE, NEWEST_UPDATED);
        Page<AnnouncementEntity> result = scope.faculty() == null
                ? schoolPage(status, pageable)
                : facultyPage(scope.faculty().getId(), status, pageable);
        return result.map(AnnouncementService::toView);
    }

    @Transactional(readOnly = true)
    public AnnouncementView getForCurrentManager(long id) {
        ActorIdentity actor = authorization.currentActor();
        AnnouncementEntity announcement = find(id);
        requireManagerScope(actor, announcement);
        return toView(announcement);
    }

    @Transactional(readOnly = true)
    public AnnouncementView getVisible(long id) {
        ActorIdentity actor = authorization.currentActor();
        AnnouncementEntity announcement = find(id);
        if (canManage(actor, announcement)) {
            return toView(announcement);
        }
        if (announcement.isPublished()
                && userNotifications.existsByRecipientIdAndEventAnnouncementId(
                        actor.accountId(), announcement.getId())) {
            return toView(announcement);
        }
        throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
    }

    @Transactional
    public AnnouncementView update(long id, long expectedVersion, DraftCommand command) {
        Announcement content = validated(command);
        ActorIdentity actor = authorization.currentActor();
        AnnouncementEntity draft = locked(id);
        requireManagerScope(actor, draft);
        requireEditableVersion(draft, expectedVersion);
        draft.revise(content.title(), content.body(), clock.instant());
        return toView(announcements.saveAndFlush(draft));
    }

    @Transactional
    public PublicationResult publish(long id, long expectedVersion) {
        ActorIdentity actor = authorization.currentActor();
        AnnouncementEntity draft = locked(id);
        requireManagerScope(actor, draft);
        requireEditableVersion(draft, expectedVersion);

        List<UserAccountEntity> scopeAccounts = draft.getFaculty() == null
                ? accounts.findAllEnabled()
                : accounts.findAllEnabledByFacultyId(draft.getFaculty().getId());
        Set<Long> recipientIds = new LinkedHashSet<>();
        for (UserAccountEntity account : scopeAccounts) {
            if (!Objects.equals(account.getId(), actor.accountId())) {
                recipientIds.add(account.getId());
            }
        }
        if (recipientIds.isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }

        UserAccountEntity publisher = accounts.getReferenceById(actor.accountId());
        Instant publishedAt = clock.instant();
        draft.publish(publisher, publishedAt);
        AnnouncementEntity published = announcements.saveAndFlush(draft);
        notifications.emit(new EmissionCommand(
                NotificationEventType.ANNOUNCEMENT_PUBLISHED,
                published.getId(),
                actor.accountId(),
                recipientIds,
                published.getTitle(),
                published.getBody(),
                published.getId(),
                NotificationTargetType.ANNOUNCEMENT,
                published.getId()));
        return new PublicationResult(
                published.getId(),
                published.getVersion(),
                recipientIds.size(),
                publishedAt);
    }

    private AnnouncementEntity locked(long id) {
        return announcements.findByIdForUpdate(id)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private ManagerScope resolveManagerScope() {
        ActorIdentity actor = authorization.currentActor();
        if (actor.roles().contains(RoleCode.ADMIN)) {
            return new ManagerScope(actor, null, SCHOOL_SOURCE);
        }
        if (actor.roles().contains(RoleCode.DEAN) && actor.facultyId() != null) {
            FacultyEntity faculty = faculties.findById(actor.facultyId())
                    .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
            return new ManagerScope(actor, faculty, faculty.getName());
        }
        throw denied();
    }

    private static void requireManagerScope(ActorIdentity actor, AnnouncementEntity announcement) {
        if (canManage(actor, announcement)) {
            return;
        }
        throw denied();
    }

    private static boolean canManage(ActorIdentity actor, AnnouncementEntity announcement) {
        FacultyEntity faculty = announcement.getFaculty();
        if (actor.roles().contains(RoleCode.ADMIN) && faculty == null) {
            return true;
        }
        return actor.roles().contains(RoleCode.DEAN)
                && faculty != null
                && Objects.equals(actor.facultyId(), faculty.getId());
    }

    private static void requireEditableVersion(AnnouncementEntity announcement, long expectedVersion) {
        if (announcement.isPublished() || expectedVersion < 0
                || announcement.getVersion() != expectedVersion) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
    }

    private static Announcement validated(DraftCommand command) {
        if (command == null) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        try {
            return new Announcement(command.title(), command.body());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    private AnnouncementEntity find(long id) {
        return announcements.findById(id)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private Page<AnnouncementEntity> schoolPage(
            AnnouncementStatusFilter status,
            PageRequest pageable) {
        return switch (status) {
            case ALL -> announcements.findByFacultyIsNull(pageable);
            case DRAFT -> announcements.findByFacultyIsNullAndPublishedAtIsNull(pageable);
            case PUBLISHED -> announcements.findByFacultyIsNullAndPublishedAtIsNotNull(pageable);
        };
    }

    private Page<AnnouncementEntity> facultyPage(
            long facultyId,
            AnnouncementStatusFilter status,
            PageRequest pageable) {
        return switch (status) {
            case ALL -> announcements.findByFacultyId(facultyId, pageable);
            case DRAFT -> announcements.findByFacultyIdAndPublishedAtIsNull(facultyId, pageable);
            case PUBLISHED -> announcements.findByFacultyIdAndPublishedAtIsNotNull(facultyId, pageable);
        };
    }

    private static AnnouncementView toView(AnnouncementEntity announcement) {
        FacultyEntity faculty = announcement.getFaculty();
        return new AnnouncementView(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getSourceName(),
                faculty == null ? null : faculty.getId(),
                announcement.getCreatedAt(),
                announcement.getUpdatedAt(),
                announcement.getPublishedAt(),
                announcement.getVersion());
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }

    private record ManagerScope(
            ActorIdentity actor,
            FacultyEntity faculty,
            String sourceName) {
    }

    public record DraftCommand(String title, String body) {
    }

    public enum AnnouncementStatusFilter {
        ALL,
        DRAFT,
        PUBLISHED
    }

    public record ManagerScopeView(String sourceName, Long facultyId) {
    }

    public record AnnouncementView(
            Long id,
            String title,
            String body,
            String sourceName,
            Long facultyId,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt,
            long version) {
        public boolean published() {
            return publishedAt != null;
        }
    }

    public record PublicationResult(
            Long id,
            long version,
            int recipientCount,
            Instant publishedAt) {
    }
}

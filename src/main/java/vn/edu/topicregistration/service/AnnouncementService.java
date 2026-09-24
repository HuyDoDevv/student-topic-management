package vn.edu.topicregistration.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.context.annotation.Profile;
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
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;

@Service
@Profile("!preview & !test")
public class AnnouncementService {
    private static final String SCHOOL_SOURCE = "Nhà trường";

    private final AnnouncementRepository announcements;
    private final UserAccountRepository accounts;
    private final FacultyRepository faculties;
    private final NotificationService notifications;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public AnnouncementService(
            AnnouncementRepository announcements,
            UserAccountRepository accounts,
            FacultyRepository faculties,
            NotificationService notifications,
            AuthorizationPolicy authorization,
            Clock clock) {
        this.announcements = announcements;
        this.accounts = accounts;
        this.faculties = faculties;
        this.notifications = notifications;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional
    public AnnouncementView create(DraftCommand command) {
        Announcement content = validated(command);
        ManagerScope scope = currentManagerScope();
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

    private ManagerScope currentManagerScope() {
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
        FacultyEntity faculty = announcement.getFaculty();
        if (actor.roles().contains(RoleCode.ADMIN) && faculty == null) {
            return;
        }
        if (actor.roles().contains(RoleCode.DEAN)
                && faculty != null
                && Objects.equals(actor.facultyId(), faculty.getId())) {
            return;
        }
        throw denied();
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

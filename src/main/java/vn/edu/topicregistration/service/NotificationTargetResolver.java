package vn.edu.topicregistration.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import vn.edu.topicregistration.entity.FinalResultStatus;
import vn.edu.topicregistration.entity.GroupInvitationStatus;
import vn.edu.topicregistration.entity.NotificationEventEntity;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.AnnouncementRepository;
import vn.edu.topicregistration.repository.FinalResultRepository;
import vn.edu.topicregistration.repository.GroupInvitationRepository;
import vn.edu.topicregistration.repository.StudentGroupRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;

@Component
@Profile("!test")
public class NotificationTargetResolver {
    public static final String UNAVAILABLE = "/notifications?target=unavailable";

    private final AnnouncementRepository announcements;
    private final GroupInvitationRepository invitations;
    private final StudentGroupRepository groups;
    private final TopicAuthorService topics;
    private final RegistrationService registrations;
    private final ScoringService scoring;
    private final FinalResultRepository results;
    private final Clock clock;

    public NotificationTargetResolver(
            AnnouncementRepository announcements,
            GroupInvitationRepository invitations,
            StudentGroupRepository groups,
            TopicAuthorService topics,
            RegistrationService registrations,
            ScoringService scoring,
            FinalResultRepository results,
            Clock clock) {
        this.announcements = announcements;
        this.invitations = invitations;
        this.groups = groups;
        this.topics = topics;
        this.registrations = registrations;
        this.scoring = scoring;
        this.results = results;
        this.clock = clock;
    }

    public String resolve(NotificationEventEntity event, ActorIdentity actor) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(actor, "actor");
        NotificationTargetType type = event.getTargetType();
        Long targetId = event.getTargetId();
        if (type == NotificationTargetType.NONE) {
            return targetId == null ? "/notifications" : UNAVAILABLE;
        }
        if (type == null || targetId == null || targetId <= 0) {
            return UNAVAILABLE;
        }
        long id = targetId;
        return switch (type) {
            case ANNOUNCEMENT -> announcement(event, id);
            case GROUP_INVITATION -> invitation(id, actor);
            case GROUP -> group(id, actor);
            case TOPIC -> topic(id);
            case REGISTRATION -> registration(id);
            case DIRECT_GRADING -> directGrading(id);
            case COUNCIL -> council(id);
            case RESULT -> result(id, actor);
            case NONE -> throw new IllegalStateException("NONE handled before target switch");
        };
    }

    private String announcement(NotificationEventEntity event, long id) {
        if (event.getAnnouncement() == null
                || !Objects.equals(event.getAnnouncement().getId(), id)) {
            return UNAVAILABLE;
        }
        return announcements.findById(id)
                .filter(item -> item.isPublished())
                .map(item -> "/announcements/" + id)
                .orElse(UNAVAILABLE);
    }

    private String invitation(long id, ActorIdentity actor) {
        if (!actor.roles().contains(RoleCode.STUDENT)) {
            return UNAVAILABLE;
        }
        Instant now = clock.instant();
        return invitations.findById(id)
                .filter(item -> Objects.equals(item.getStudent().getId(), actor.accountId()))
                .filter(item -> Objects.equals(
                        item.getPeriod().getFaculty().getId(), actor.facultyId()))
                .filter(item -> item.getStatus() == GroupInvitationStatus.PENDING)
                .filter(item -> item.getGroup().isActive())
                .filter(item -> !now.isBefore(item.getPeriod().getStudentStart())
                        && !now.isAfter(item.getPeriod().getStudentEnd()))
                .map(item -> "/group-invitations")
                .orElse(UNAVAILABLE);
    }

    private String group(long id, ActorIdentity actor) {
        if (!actor.roles().contains(RoleCode.STUDENT)) {
            return UNAVAILABLE;
        }
        return groups.findByIdVisibleToStudent(id, actor.accountId())
                .filter(item -> Objects.equals(
                        item.getPeriod().getFaculty().getId(), actor.facultyId()))
                .map(item -> "/groups/" + id)
                .orElse(UNAVAILABLE);
    }

    private String topic(long id) {
        try {
            var topic = topics.getOwn(id);
            topics.formContext(topic.periodId());
            return "/topics/" + id;
        } catch (ApplicationException exception) {
            if (exception.code() == ApplicationErrorCode.RESOURCE_NOT_FOUND
                    || exception.code() == ApplicationErrorCode.WINDOW_CLOSED
                    || exception.code() == ApplicationErrorCode.TOPIC_DEPARTMENT_REQUIRED) {
                return UNAVAILABLE;
            }
            throw exception;
        }
    }

    private String registration(long id) {
        try {
            registrations.getVisible(id);
            return "/registrations/" + id;
        } catch (ApplicationException exception) {
            if (exception.code() == ApplicationErrorCode.RESOURCE_NOT_FOUND) {
                return UNAVAILABLE;
            }
            throw exception;
        }
    }

    private String directGrading(long id) {
        return scoring.listForCurrentLecturer().stream()
                .filter(assignment -> Objects.equals(assignment.councilId(), id))
                .filter(ScoringService.ScoringAssignmentView::directGrading)
                .findFirst()
                .map(assignment -> directWorkspace(id, assignment.registrationId()))
                .orElse(UNAVAILABLE);
    }

    private String directWorkspace(long councilId, long registrationId) {
        try {
            scoring.workspace(councilId, registrationId);
            return "/lecturer/scoring/" + councilId + "/" + registrationId;
        } catch (ApplicationException exception) {
            if (exception.code() == ApplicationErrorCode.RESOURCE_NOT_FOUND
                    || exception.code() == ApplicationErrorCode.CONFLICT
                    || exception.code() == ApplicationErrorCode.INVALID_COUNCIL_STATE) {
                return UNAVAILABLE;
            }
            throw exception;
        }
    }

    private String council(long id) {
        boolean visible = scoring.listForCurrentLecturer().stream()
                .anyMatch(assignment -> Objects.equals(assignment.councilId(), id)
                        && !assignment.directGrading());
        return visible ? "/lecturer/scoring" : UNAVAILABLE;
    }

    private String result(long id, ActorIdentity actor) {
        if (!actor.roles().contains(RoleCode.STUDENT)) {
            return UNAVAILABLE;
        }
        return results.findVisibleToStudent(
                        id, actor.accountId(), FinalResultStatus.PUBLISHED)
                .map(item -> "/student/results/" + id)
                .orElse(UNAVAILABLE);
    }
}

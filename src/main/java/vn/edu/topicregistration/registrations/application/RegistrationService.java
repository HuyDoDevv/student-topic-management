package vn.edu.topicregistration.registrations.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.groups.persistence.GroupInvitationRepository;
import vn.edu.topicregistration.groups.persistence.GroupInvitationStatus;
import vn.edu.topicregistration.groups.persistence.GroupMembershipEntity;
import vn.edu.topicregistration.groups.persistence.StudentGroupEntity;
import vn.edu.topicregistration.groups.persistence.StudentGroupRepository;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodRepository;
import vn.edu.topicregistration.registrations.domain.RegistrationRules;
import vn.edu.topicregistration.registrations.domain.RegistrationRules.RegistrationRuleException;
import vn.edu.topicregistration.registrations.domain.RegistrationRules.State;
import vn.edu.topicregistration.registrations.domain.RegistrationRules.SubmissionContext;
import vn.edu.topicregistration.registrations.domain.RegistrationRules.WithdrawalContext;
import vn.edu.topicregistration.registrations.persistence.RegistrationEntity;
import vn.edu.topicregistration.registrations.persistence.RegistrationRepository;
import vn.edu.topicregistration.registrations.persistence.RegistrationStatus;
import vn.edu.topicregistration.shared.error.ApplicationErrorCode;
import vn.edu.topicregistration.shared.error.ApplicationException;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.topics.persistence.TopicEntity;
import vn.edu.topicregistration.topics.persistence.TopicRepository;

@Service
@Profile("!preview & !test")
public class RegistrationService {
    private static final List<RegistrationStatus> ACTIVE_STATUSES = List.of(
            RegistrationStatus.PENDING,
            RegistrationStatus.ACCEPTED);

    private final RegistrationPeriodRepository periods;
    private final StudentGroupRepository groups;
    private final GroupInvitationRepository invitations;
    private final TopicRepository topics;
    private final RegistrationRepository registrations;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public RegistrationService(
            RegistrationPeriodRepository periods,
            StudentGroupRepository groups,
            GroupInvitationRepository invitations,
            TopicRepository topics,
            RegistrationRepository registrations,
            AuthorizationPolicy authorization,
            Clock clock) {
        this.periods = periods;
        this.groups = groups;
        this.invitations = invitations;
        this.topics = topics;
        this.registrations = registrations;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationView submit(
            long groupId,
            long expectedGroupVersion,
            long topicId) {
        long periodId = groups.findPeriodIdByGroupId(groupId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        ActorIdentity student = currentStudentFor(period);
        StudentGroupEntity group = lockedGroup(groupId, period, expectedGroupVersion);
        TopicEntity topic = lockedTopic(topicId, period);

        boolean topicInFaculty = topic.getDepartment().getFaculty() != null
                && Objects.equals(
                        topic.getDepartment().getFaculty().getId(),
                        period.getFaculty().getId());
        if (!topicInFaculty) {
            throw new AccessDeniedException("Topic faculty scope does not match the period");
        }

        boolean leaderIsMember = group.getMembers().stream()
                .map(GroupMembershipEntity::getStudent)
                .anyMatch(member -> Objects.equals(member.getId(), group.getLeader().getId()));
        boolean pendingInvitation = invitations.existsByGroupIdAndStatus(
                groupId, GroupInvitationStatus.PENDING);
        boolean activeRegistration = registrations.existsByPeriodIdAndGroupIdAndStatusIn(
                periodId, groupId, ACTIVE_STATUSES);
        boolean topicAccepted = registrations.existsByPeriodIdAndTopicIdAndStatus(
                periodId, topicId, RegistrationStatus.ACCEPTED);
        Instant now = clock.instant();

        applyRules(() -> {
            RegistrationRules.validateSubmit(new SubmissionContext(
                    student.accountId(),
                    group.getLeader().getId(),
                    group.getMembers().size(),
                    leaderIsMember,
                    group.isActive(),
                    pendingInvitation,
                    activeRegistration,
                    Objects.equals(topic.getPeriod().getId(), periodId),
                    "PUBLISHED".equals(topic.getStatus()),
                    topicAccepted,
                    now,
                    period.getStudentStart(),
                    period.getStudentEnd()));
            return null;
        });

        RegistrationEntity saved = registrations.saveAndFlush(
                new RegistrationEntity(period, group, topic, now));
        return toView(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationView withdraw(long registrationId, long expectedVersion) {
        long periodId = registrations.findPeriodIdByRegistrationId(registrationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        ActorIdentity student = currentStudentFor(period);
        RegistrationEntity registration = registrations.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));

        if (!Objects.equals(registration.getPeriod().getId(), periodId)) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (registration.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Registration version is stale");
        }

        Instant now = clock.instant();
        applyRules(() -> {
            RegistrationRules.validateWithdraw(new WithdrawalContext(
                    student.accountId(),
                    registration.getGroup().getLeader().getId(),
                    stateOf(registration),
                    now,
                    period.getStudentStart(),
                    period.getStudentEnd()));
            return null;
        });

        registration.withdraw(now);
        registrations.flush();
        return toView(registration);
    }

    private RegistrationPeriodEntity lockedPeriod(long periodId) {
        return periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private StudentGroupEntity lockedGroup(
            long groupId,
            RegistrationPeriodEntity period,
            long expectedVersion) {
        StudentGroupEntity group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(group.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (group.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Group version is stale");
        }
        return group;
    }

    private TopicEntity lockedTopic(long topicId, RegistrationPeriodEntity period) {
        TopicEntity topic = topics.findByIdForUpdate(topicId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(topic.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.INVALID_TOPIC_STATE);
        }
        return topic;
    }

    private ActorIdentity currentStudentFor(RegistrationPeriodEntity period) {
        ActorIdentity student = authorization.requireStudentOfFaculty(
                period.getFaculty().getId());
        if (student.accountId() == null) {
            throw new AccessDeniedException("Student account is missing");
        }
        return student;
    }

    private State stateOf(RegistrationEntity registration) {
        try {
            return State.valueOf(registration.getStatus().name());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
    }

    private RegistrationView toView(RegistrationEntity registration) {
        return new RegistrationView(
                registration.getId(),
                registration.getPeriod().getId(),
                registration.getGroup().getId(),
                registration.getTopic().getId(),
                registration.getStatus(),
                registration.getSubmittedAt(),
                registration.getWithdrawnAt(),
                registration.getVersion());
    }

    private <T> T applyRules(RuleOperation<T> operation) {
        try {
            return operation.run();
        } catch (RegistrationRuleException exception) {
            switch (exception.failure()) {
                case ACCESS_DENIED -> throw new AccessDeniedException("Access denied");
                case WINDOW_CLOSED -> throw new ApplicationException(
                        ApplicationErrorCode.WINDOW_CLOSED);
                case INVALID_GROUP_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_GROUP_STATE);
                case PENDING_INVITATION -> throw new ApplicationException(
                        ApplicationErrorCode.GROUP_INVITATION_CONFLICT);
                case ACTIVE_REGISTRATION -> throw new ApplicationException(
                        ApplicationErrorCode.ACTIVE_REGISTRATION_EXISTS);
                case INVALID_TOPIC_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_TOPIC_STATE);
                case TOPIC_TAKEN -> throw new ApplicationException(
                        ApplicationErrorCode.TOPIC_TAKEN);
                case INVALID_REGISTRATION_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.CONFLICT);
            }
            throw new IllegalStateException("Unknown registration rule failure", exception);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    @FunctionalInterface
    private interface RuleOperation<T> {
        T run();
    }

    public record RegistrationView(
            long id,
            long periodId,
            long groupId,
            long topicId,
            RegistrationStatus status,
            Instant submittedAt,
            Instant withdrawnAt,
            long version) {
    }
}

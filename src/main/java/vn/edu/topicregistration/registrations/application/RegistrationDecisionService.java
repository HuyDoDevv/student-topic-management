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

import vn.edu.topicregistration.identity.persistence.UserAccountEntity;
import vn.edu.topicregistration.identity.persistence.UserAccountRepository;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodRepository;
import vn.edu.topicregistration.registrations.domain.RegistrationRules;
import vn.edu.topicregistration.registrations.domain.RegistrationRules.RegistrationRuleException;
import vn.edu.topicregistration.registrations.domain.RegistrationRules.State;
import vn.edu.topicregistration.registrations.persistence.RegistrationEntity;
import vn.edu.topicregistration.registrations.persistence.RegistrationRejectionKind;
import vn.edu.topicregistration.registrations.persistence.RegistrationRepository;
import vn.edu.topicregistration.registrations.persistence.RegistrationStatus;
import vn.edu.topicregistration.shared.error.ApplicationErrorCode;
import vn.edu.topicregistration.shared.error.ApplicationException;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy.ActorIdentity;

@Service
@Profile("!preview & !test")
public class RegistrationDecisionService {
    private final RegistrationPeriodRepository periods;
    private final RegistrationRepository registrations;
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public RegistrationDecisionService(
            RegistrationPeriodRepository periods,
            RegistrationRepository registrations,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            Clock clock) {
        this.periods = periods;
        this.registrations = registrations;
        this.accounts = accounts;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DecisionResult accept(long registrationId, long expectedVersion) {
        LockedRegistration locked = lockedRegistration(registrationId, expectedVersion);
        RegistrationEntity selected = locked.registration();
        long periodId = locked.period().getId();
        long topicId = selected.getTopic().getId();

        boolean topicAlreadyAccepted = registrations.existsByPeriodIdAndTopicIdAndStatus(
                periodId, topicId, RegistrationStatus.ACCEPTED);
        applyRules(() -> {
            RegistrationRules.validateAccept(stateOf(selected), topicAlreadyAccepted);
            return null;
        });

        List<RegistrationEntity> pending = registrations.findAllPendingByTopicForUpdate(
                periodId, topicId);
        if (pending.stream().noneMatch(item -> Objects.equals(item.getId(), registrationId))) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }

        Instant decidedAt = clock.instant();
        selected.accept(locked.actorAccount(), decidedAt);
        int rejectedCount = 0;
        for (RegistrationEntity registration : pending) {
            if (!Objects.equals(registration.getId(), registrationId)) {
                registration.rejectBecauseTopicTaken(locked.actorAccount(), decidedAt);
                rejectedCount++;
            }
        }
        registrations.flush();
        return toResult(selected, rejectedCount);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DecisionResult reject(
            long registrationId,
            long expectedVersion,
            String reason) {
        LockedRegistration locked = lockedRegistration(registrationId, expectedVersion);
        String normalizedReason = applyRules(() -> RegistrationRules.validateManualReject(
                stateOf(locked.registration()), reason));

        locked.registration().rejectManually(
                locked.actorAccount(), normalizedReason, clock.instant());
        registrations.flush();
        return toResult(locked.registration(), 0);
    }

    private LockedRegistration lockedRegistration(
            long registrationId,
            long expectedVersion) {
        long periodId = registrations.findPeriodIdByRegistrationId(registrationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        ActorIdentity dean = authorization.requireDeanOfFaculty(period.getFaculty().getId());
        if (dean.accountId() == null) {
            throw new AccessDeniedException("Dean account is missing");
        }

        RegistrationEntity registration = registrations.findByIdForUpdate(registrationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(registration.getPeriod().getId(), periodId)) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (registration.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Registration version is stale");
        }
        UserAccountEntity actorAccount = accounts.findById(dean.accountId())
                .orElseThrow(() -> new AccessDeniedException("Dean account no longer exists"));
        return new LockedRegistration(period, registration, actorAccount);
    }

    private State stateOf(RegistrationEntity registration) {
        try {
            return State.valueOf(registration.getStatus().name());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
    }

    private DecisionResult toResult(
            RegistrationEntity registration,
            int autoRejectedCount) {
        return new DecisionResult(
                registration.getId(),
                registration.getPeriod().getId(),
                registration.getGroup().getId(),
                registration.getTopic().getId(),
                registration.getStatus(),
                registration.getRejectionKind(),
                registration.getRejectionReason(),
                registration.getDecidedAt(),
                registration.getVersion(),
                autoRejectedCount);
    }

    private <T> T applyRules(RuleOperation<T> operation) {
        try {
            return operation.run();
        } catch (RegistrationRuleException exception) {
            switch (exception.failure()) {
                case TOPIC_TAKEN -> throw new ApplicationException(
                        ApplicationErrorCode.TOPIC_TAKEN);
                case INVALID_REGISTRATION_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.CONFLICT);
                case VALIDATION_FAILED -> throw new ApplicationException(
                        ApplicationErrorCode.VALIDATION_FAILED);
                case ACCESS_DENIED -> throw new AccessDeniedException("Access denied");
                case WINDOW_CLOSED -> throw new ApplicationException(
                        ApplicationErrorCode.WINDOW_CLOSED);
                case INVALID_GROUP_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_GROUP_STATE);
                case GROUP_INCOMPLETE -> throw new ApplicationException(
                        ApplicationErrorCode.GROUP_INCOMPLETE);
                case PENDING_INVITATION -> throw new ApplicationException(
                        ApplicationErrorCode.GROUP_INVITATION_CONFLICT);
                case ACTIVE_REGISTRATION -> throw new ApplicationException(
                        ApplicationErrorCode.ACTIVE_REGISTRATION_EXISTS);
                case INVALID_TOPIC_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_TOPIC_STATE);
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

    private record LockedRegistration(
            RegistrationPeriodEntity period,
            RegistrationEntity registration,
            UserAccountEntity actorAccount) {
    }

    public record DecisionResult(
            long registrationId,
            long periodId,
            long groupId,
            long topicId,
            RegistrationStatus status,
            RegistrationRejectionKind rejectionKind,
            String rejectionReason,
            Instant decidedAt,
            long version,
            int autoRejectedCount) {
    }
}

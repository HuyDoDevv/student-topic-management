package vn.edu.topicregistration.periods.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.identity.persistence.FacultyEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodRepository;
import vn.edu.topicregistration.shared.error.ApplicationErrorCode;
import vn.edu.topicregistration.shared.error.ApplicationException;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy.ActorIdentity;

@Service
@Profile("!preview & !test")
public class PeriodTransactionService {
    private final RegistrationPeriodRepository periods;
    private final AuthorizationPolicy authorization;
    private final EntityManager entityManager;
    private final Clock clock;

    public PeriodTransactionService(
            RegistrationPeriodRepository periods,
            AuthorizationPolicy authorization,
            EntityManager entityManager,
            Clock clock) {
        this.periods = periods;
        this.authorization = authorization;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationPeriodEntity create(
            long facultyId,
            Function<CreationContext, RegistrationPeriodEntity> validatedFactory) {
        Objects.requireNonNull(validatedFactory, "validatedFactory");
        ActorIdentity actor = authorization.requireDeanOfFaculty(facultyId);
        FacultyEntity faculty = entityManager.find(FacultyEntity.class, facultyId);
        if (faculty == null) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }

        CreationContext context = new CreationContext(faculty, actor, clock.instant());
        RegistrationPeriodEntity candidate = Objects.requireNonNull(
                validatedFactory.apply(context), "validatedFactory result");
        if (candidate.getId() != null
                || candidate.getFaculty() == null
                || !Objects.equals(candidate.getFaculty().getId(), facultyId)) {
            throw new AccessDeniedException("Period faculty must match the authorized scope");
        }
        return periods.saveAndFlush(candidate);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T mutate(
            long periodId,
            long expectedVersion,
            Function<LockedPeriodContext, T> mutation) {
        Objects.requireNonNull(mutation, "mutation");

        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        ActorIdentity actor = authorization.requireDeanOfFaculty(period.getFaculty().getId());
        Instant now = clock.instant();
        if (period.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Registration period version is stale");
        }

        return mutation.apply(new LockedPeriodContext(period, actor, now));
    }

    public record CreationContext(
            FacultyEntity faculty,
            ActorIdentity actor,
            Instant now) {
    }

    public record LockedPeriodContext(
            RegistrationPeriodEntity period,
            ActorIdentity actor,
            Instant now) {
    }
}

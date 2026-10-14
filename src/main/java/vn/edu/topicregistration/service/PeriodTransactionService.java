package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;

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

import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;

@Service
@Profile("!test")
public class PeriodTransactionService {
    private final RegistrationPeriodRepository periods;
    private final DepartmentRepository departments;
    private final AuthorizationPolicy authorization;
    private final EntityManager entityManager;
    private final Clock clock;

    public PeriodTransactionService(
            RegistrationPeriodRepository periods,
            DepartmentRepository departments,
            AuthorizationPolicy authorization,
            EntityManager entityManager,
            Clock clock) {
        this.periods = periods;
        this.departments = departments;
        this.authorization = authorization;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationPeriodEntity create(
            long facultyId,
            long departmentId,
            Function<CreationContext, RegistrationPeriodEntity> validatedFactory) {
        Objects.requireNonNull(validatedFactory, "validatedFactory");
        ActorIdentity actor = authorization.requireDeanOfFaculty(facultyId);
        FacultyEntity faculty = entityManager.find(FacultyEntity.class, facultyId);
        if (faculty == null) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }

        DepartmentEntity department = requireDepartment(departmentId, facultyId);

        CreationContext context = new CreationContext(faculty, department, actor, clock.instant());
        RegistrationPeriodEntity candidate = Objects.requireNonNull(
                validatedFactory.apply(context), "validatedFactory result");
        if (candidate.getId() != null
                || candidate.getFaculty() == null
                || !Objects.equals(candidate.getFaculty().getId(), facultyId)
                || candidate.getDepartment() == null
                || !Objects.equals(candidate.getDepartment().getId(), departmentId)) {
            throw new AccessDeniedException("Period faculty and department must match the authorized scope");
        }
        return periods.saveAndFlush(candidate);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public <T> T mutate(
            long periodId,
            long expectedVersion,
            Long requestedDepartmentId,
            Function<LockedPeriodContext, T> mutation) {
        Objects.requireNonNull(mutation, "mutation");

        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        ActorIdentity actor = authorization.requireDeanOfFaculty(period.getFaculty().getId());
        Instant now = clock.instant();
        if (period.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Registration period version is stale");
        }

        DepartmentEntity currentDepartment = period.getDepartment();
        Long currentDepartmentId = currentDepartment == null ? null : currentDepartment.getId();
        if (!Objects.equals(currentDepartmentId, requestedDepartmentId)) {
            if (currentDepartment == null || requestedDepartmentId == null
                    || requestedDepartmentId <= 0
                    || !now.isBefore(period.getLecturerStart())
                    || periods.dependentDataFlag(periodId) != 0) {
                throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
            }
            period.changeDepartment(requireDepartment(
                    requestedDepartmentId, period.getFaculty().getId()));
        }

        return mutation.apply(new LockedPeriodContext(period, actor, now));
    }

    private DepartmentEntity requireDepartment(long departmentId, long facultyId) {
        if (departmentId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        return departments.findByIdAndFacultyId(departmentId, facultyId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED));
    }

    public record CreationContext(
            FacultyEntity faculty,
            DepartmentEntity department,
            ActorIdentity actor,
            Instant now) {
    }

    public record LockedPeriodContext(
            RegistrationPeriodEntity period,
            ActorIdentity actor,
            Instant now) {
    }
}

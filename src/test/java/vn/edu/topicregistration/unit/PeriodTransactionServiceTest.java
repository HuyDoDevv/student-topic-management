package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.service.PeriodTransactionService;
import vn.edu.topicregistration.service.PeriodTransactionService.CreationContext;
import vn.edu.topicregistration.service.PeriodTransactionService.LockedPeriodContext;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;

class PeriodTransactionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    RegistrationPeriodRepository periods;
    DepartmentRepository departments;
    AuthorizationPolicy authorization;
    EntityManager entityManager;
    Clock clock;
    PeriodTransactionService transactions;

    @BeforeEach
    void setUp() {
        periods = mock(RegistrationPeriodRepository.class);
        departments = mock(DepartmentRepository.class);
        authorization = mock(AuthorizationPolicy.class);
        entityManager = mock(EntityManager.class);
        clock = mock(Clock.class);
        transactions = new PeriodTransactionService(
                periods, departments, authorization, entityManager, clock);
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void mutationLocksBeforeReloadingScopeTimeAndVersion() {
        FacultyEntity faculty = faculty(21L);
        RegistrationPeriodEntity period = period(7L, faculty, 3L);
        ActorIdentity actor = actor(9L, 21L);
        when(periods.findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(authorization.requireDeanOfFaculty(21L)).thenReturn(actor);
        @SuppressWarnings("unchecked")
        Function<LockedPeriodContext, String> mutation = mock(Function.class);
        when(mutation.apply(org.mockito.ArgumentMatchers.any())).thenReturn("updated");

        String result = transactions.mutate(7L, 3L, null, mutation);

        assertEquals("updated", result);
        InOrder order = inOrder(periods, authorization, clock, period, mutation);
        order.verify(periods).findByIdForUpdate(7L);
        order.verify(period).getFaculty();
        order.verify(authorization).requireDeanOfFaculty(21L);
        order.verify(clock).instant();
        order.verify(period).getVersion();
        order.verify(mutation).apply(org.mockito.ArgumentMatchers.argThat(context ->
                context.period() == period && context.actor() == actor && NOW.equals(context.now())));
    }

    @Test
    void staleVersionStopsBeforeTheMutationCallback() {
        RegistrationPeriodEntity period = period(7L, faculty(21L), 4L);
        when(periods.findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(authorization.requireDeanOfFaculty(21L)).thenReturn(actor(9L, 21L));
        @SuppressWarnings("unchecked")
        Function<LockedPeriodContext, String> mutation = mock(Function.class);

        assertThrows(OptimisticLockingFailureException.class,
                () -> transactions.mutate(7L, 3L, null, mutation));

        verify(mutation, never()).apply(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void missingPeriodFailsClosedBeforeAuthorizationOrMutation() {
        when(periods.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        @SuppressWarnings("unchecked")
        Function<LockedPeriodContext, String> mutation = mock(Function.class);

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> transactions.mutate(404L, 0L, null, mutation));

        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, failure.code());
        verify(authorization, never()).requireDeanOfFaculty(org.mockito.ArgumentMatchers.anyLong());
        verify(mutation, never()).apply(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createAuthorizesAndValidatesTheFactoryResultBeforeInsert() {
        FacultyEntity faculty = faculty(21L);
        ActorIdentity actor = actor(9L, 21L);
        RegistrationPeriodEntity candidate = period(null, faculty, 0L);
        DepartmentEntity department = department(11L);
        when(candidate.getDepartment()).thenReturn(department);
        when(authorization.requireDeanOfFaculty(21L)).thenReturn(actor);
        when(entityManager.find(FacultyEntity.class, 21L)).thenReturn(faculty);
        when(departments.findByIdAndFacultyId(11L, 21L)).thenReturn(Optional.of(department));
        when(periods.saveAndFlush(candidate)).thenReturn(candidate);
        @SuppressWarnings("unchecked")
        Function<CreationContext, RegistrationPeriodEntity> factory = mock(Function.class);
        when(factory.apply(org.mockito.ArgumentMatchers.argThat(context ->
                context.faculty() == faculty
                        && context.department() == department
                        && context.actor() == actor
                        && NOW.equals(context.now())))).thenReturn(candidate);

        RegistrationPeriodEntity saved = transactions.create(21L, 11L, factory);

        assertSame(candidate, saved);
        InOrder order = inOrder(authorization, entityManager, departments, clock, factory, periods);
        order.verify(authorization).requireDeanOfFaculty(21L);
        order.verify(entityManager).find(FacultyEntity.class, 21L);
        order.verify(departments).findByIdAndFacultyId(11L, 21L);
        order.verify(clock).instant();
        order.verify(factory).apply(org.mockito.ArgumentMatchers.any());
        order.verify(periods).saveAndFlush(candidate);
    }

    @Test
    void createRejectsDepartmentOutsideAuthorizedFaculty() {
        when(authorization.requireDeanOfFaculty(21L)).thenReturn(actor(9L, 21L));
        FacultyEntity faculty = faculty(21L);
        when(entityManager.find(FacultyEntity.class, 21L)).thenReturn(faculty);
        @SuppressWarnings("unchecked")
        Function<CreationContext, RegistrationPeriodEntity> factory = mock(Function.class);

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> transactions.create(21L, 99L, factory));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
        verify(factory, never()).apply(org.mockito.ArgumentMatchers.any());
        verify(periods, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mutationChangesDepartmentOnlyBeforeOpeningAndWithoutDependentData() {
        RegistrationPeriodEntity period = period(7L, faculty(21L), 0L);
        DepartmentEntity original = department(11L);
        DepartmentEntity replacement = department(12L);
        when(period.getDepartment()).thenReturn(original);
        when(period.getLecturerStart()).thenReturn(NOW.plusSeconds(60));
        when(periods.findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(authorization.requireDeanOfFaculty(21L)).thenReturn(actor(9L, 21L));
        when(departments.findByIdAndFacultyId(12L, 21L)).thenReturn(Optional.of(replacement));

        String result = transactions.mutate(7L, 0L, 12L, context -> "updated");

        assertEquals("updated", result);
        verify(periods).dependentDataFlag(7L);
        verify(period).changeDepartment(replacement);
    }

    @Test
    void mutationRejectsDepartmentChangeAfterOpeningOrDependentData() {
        RegistrationPeriodEntity period = period(7L, faculty(21L), 0L);
        DepartmentEntity original = department(11L);
        when(period.getDepartment()).thenReturn(original);
        when(periods.findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(authorization.requireDeanOfFaculty(21L)).thenReturn(actor(9L, 21L));
        @SuppressWarnings("unchecked")
        Function<LockedPeriodContext, String> mutation = mock(Function.class);

        when(period.getLecturerStart()).thenReturn(NOW);
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED,
                assertThrows(ApplicationException.class,
                        () -> transactions.mutate(7L, 0L, 12L, mutation)).code());

        when(period.getLecturerStart()).thenReturn(NOW.plusSeconds(60));
        when(periods.dependentDataFlag(7L)).thenReturn(1);
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED,
                assertThrows(ApplicationException.class,
                        () -> transactions.mutate(7L, 0L, 12L, mutation)).code());
        verify(mutation, never()).apply(org.mockito.ArgumentMatchers.any());
        verify(period, never()).changeDepartment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void legacyPeriodCannotBeAssignedByRegularEdit() {
        RegistrationPeriodEntity period = period(7L, faculty(21L), 0L);
        when(periods.findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(authorization.requireDeanOfFaculty(21L)).thenReturn(actor(9L, 21L));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> transactions.mutate(7L, 0L, 11L, context -> null));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
        verify(period, never()).changeDepartment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mutationRejectsDepartmentOutsideThePeriodFaculty() {
        RegistrationPeriodEntity period = period(7L, faculty(21L), 0L);
        DepartmentEntity original = department(11L);
        when(period.getDepartment()).thenReturn(original);
        when(period.getLecturerStart()).thenReturn(NOW.plusSeconds(60));
        when(periods.findByIdForUpdate(7L)).thenReturn(Optional.of(period));
        when(authorization.requireDeanOfFaculty(21L)).thenReturn(actor(9L, 21L));

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> transactions.mutate(7L, 0L, 99L, context -> null));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
        verify(departments).findByIdAndFacultyId(99L, 21L);
        verify(period, never()).changeDepartment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transactionEntryPointsRequireReadCommittedIsolation() throws Exception {
        assertReadCommitted("create", long.class, long.class, Function.class);
        assertReadCommitted("mutate", long.class, long.class, Long.class, Function.class);
    }

    private void assertReadCommitted(String name, Class<?>... parameterTypes) throws Exception {
        Method method = PeriodTransactionService.class.getMethod(name, parameterTypes);
        Transactional transaction = method.getAnnotation(Transactional.class);
        assertEquals(Isolation.READ_COMMITTED, transaction.isolation());
    }

    private FacultyEntity faculty(long id) {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(id);
        return faculty;
    }

    private DepartmentEntity department(long id) {
        DepartmentEntity department = mock(DepartmentEntity.class);
        when(department.getId()).thenReturn(id);
        return department;
    }

    private RegistrationPeriodEntity period(Long id, FacultyEntity faculty, long version) {
        RegistrationPeriodEntity period = mock(RegistrationPeriodEntity.class);
        when(period.getId()).thenReturn(id);
        when(period.getFaculty()).thenReturn(faculty);
        when(period.getVersion()).thenReturn(version);
        return period;
    }

    private ActorIdentity actor(long id, long facultyId) {
        return new ActorIdentity(id, Set.of(RoleCode.LECTURER, RoleCode.DEAN), facultyId);
    }
}

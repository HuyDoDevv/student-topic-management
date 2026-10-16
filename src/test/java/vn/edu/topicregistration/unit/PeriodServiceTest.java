package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.service.PeriodService;
import vn.edu.topicregistration.service.PeriodTransactionService;
import vn.edu.topicregistration.service.PeriodTransactionService.CreationContext;
import vn.edu.topicregistration.service.PeriodTransactionService.LockedPeriodContext;
import vn.edu.topicregistration.service.PeriodRules.PeriodConfiguration;
import vn.edu.topicregistration.service.PeriodRules.RegistrationState;
import vn.edu.topicregistration.service.PeriodRules.TopicType;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;

class PeriodServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant LECTURER_START = Instant.parse("2026-09-10T01:00:00Z");
    private static final Instant LECTURER_END = Instant.parse("2026-09-10T03:00:00Z");
    private static final Instant STUDENT_START = Instant.parse("2026-09-10T04:00:00Z");
    private static final Instant STUDENT_END = Instant.parse("2026-09-10T06:00:00Z");

    PeriodTransactionService transactions;
    RegistrationPeriodRepository periods;
    DepartmentRepository departments;
    AuthorizationPolicy authorization;
    Clock clock;
    FacultyEntity faculty;
    DepartmentEntity department;
    ActorIdentity dean;
    PeriodService service;

    @BeforeEach
    void setUp() {
        transactions = mock(PeriodTransactionService.class);
        periods = mock(RegistrationPeriodRepository.class);
        departments = mock(DepartmentRepository.class);
        authorization = mock(AuthorizationPolicy.class);
        clock = mock(Clock.class);
        faculty = mock(FacultyEntity.class);
        department = mock(DepartmentEntity.class);
        dean = new ActorIdentity(
                9L, Set.of(RoleCode.DEAN), 21L);
        when(faculty.getId()).thenReturn(21L);
        when(department.getId()).thenReturn(11L);
        when(department.getName()).thenReturn("Công nghệ phần mềm");
        when(authorization.requireRole(RoleCode.DEAN)).thenReturn(dean);
        when(clock.instant()).thenReturn(NOW);
        service = new PeriodService(transactions, periods, departments, authorization, clock);
    }

    @Test
    void createValidatesInsideTheTransactionAndReturnsDerivedState() {
        runCreateFactoryInsideTransaction();

        var result = service.create(11L, configuration("  Đợt HK1  "));

        assertEquals("Đợt HK1", result.name());
        assertEquals(TopicType.COURSE, result.topicType());
        assertEquals(RegistrationState.BEFORE_LECTURER, result.state());
        assertEquals(21L, result.facultyId());
        assertEquals(11L, result.departmentId());
        assertEquals("Công nghệ phần mềm", result.departmentName());
        verify(transactions).create(eq(21L), eq(11L), any());
    }

    @Test
    void invalidCreateBecomesAStableValidationErrorBeforeInsert() {
        runCreateFactoryInsideTransaction();

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> service.create(11L, configuration("  ")));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
    }

    @Test
    void createRejectsMissingDepartmentBeforeStartingTransaction() {
        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> service.create(null, configuration("Đợt HK1")));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
        verify(transactions, never()).create(eq(21L), eq(11L), any());
    }

    @Test
    void updateUsesLockedTimeAppliesAllowedExtensionAndFlushesVersion() {
        RegistrationPeriodEntity current = new RegistrationPeriodEntity(
                faculty,
                department,
                "Đợt HK1",
                "COURSE",
                LECTURER_START,
                LECTURER_END,
                STUDENT_START,
                STUDENT_END,
                null,
                null);
        Instant openingTime = LECTURER_START;
        when(transactions.mutate(eq(7L), eq(0L), eq(11L), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<LockedPeriodContext, Object> mutation = invocation.getArgument(3);
            return mutation.apply(new LockedPeriodContext(current, dean, openingTime));
        });
        PeriodConfiguration extension = new PeriodConfiguration(
                "Đợt HK1",
                TopicType.COURSE,
                LECTURER_START,
                LECTURER_END.plusSeconds(60),
                STUDENT_START,
                STUDENT_END.plusSeconds(60),
                null,
                null);

        var result = service.update(7L, 0L, 11L, extension);

        assertEquals(LECTURER_END.plusSeconds(60), result.lecturerEnd());
        assertEquals(STUDENT_END.plusSeconds(60), result.studentEnd());
        assertEquals(RegistrationState.LECTURER_OPEN, result.state());
        verify(periods).flush();
    }

    @Test
    void deanListIncludesExpiredPeriodsForStatusFiltering() {
        RegistrationPeriodEntity active = period(
                "Đợt còn hiệu lực", NOW.minusSeconds(300), NOW.plusSeconds(300));
        RegistrationPeriodEntity expired = period(
                "Đợt đã kết thúc", NOW.minusSeconds(600), NOW.minusSeconds(1));
        when(periods.findAllByFacultyIdOrderByLecturerStartDesc(21L))
                .thenReturn(List.of(active, expired));

        var result = service.listForCurrentDean();

        assertEquals(2, result.size());
        assertEquals("Đợt còn hiệu lực", result.getFirst().name());
        assertEquals("Đợt đã kết thúc", result.getLast().name());
        assertEquals(RegistrationState.REGISTRATION_CLOSED, result.getLast().state());
    }

    private void runCreateFactoryInsideTransaction() {
        when(transactions.create(eq(21L), eq(11L), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<CreationContext, RegistrationPeriodEntity> factory = invocation.getArgument(2);
            return factory.apply(new CreationContext(faculty, department, dean, NOW));
        });
    }

    private PeriodConfiguration configuration(String name) {
        return new PeriodConfiguration(
                name,
                TopicType.COURSE,
                LECTURER_START,
                LECTURER_END,
                STUDENT_START,
                STUDENT_END,
                null,
                null);
    }

    private RegistrationPeriodEntity period(
            String name,
            Instant studentStart,
            Instant studentEnd) {
        return new RegistrationPeriodEntity(
                faculty,
                name,
                "COURSE",
                studentStart.minusSeconds(600),
                studentStart.minusSeconds(300),
                studentStart,
                studentEnd,
                null,
                null);
    }
}

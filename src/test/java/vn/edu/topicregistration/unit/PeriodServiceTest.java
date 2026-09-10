package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.identity.persistence.FacultyEntity;
import vn.edu.topicregistration.periods.application.PeriodService;
import vn.edu.topicregistration.periods.application.PeriodTransactionService;
import vn.edu.topicregistration.periods.application.PeriodTransactionService.CreationContext;
import vn.edu.topicregistration.periods.application.PeriodTransactionService.LockedPeriodContext;
import vn.edu.topicregistration.periods.domain.PeriodRules.PeriodConfiguration;
import vn.edu.topicregistration.periods.domain.PeriodRules.RegistrationState;
import vn.edu.topicregistration.periods.domain.PeriodRules.TopicType;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodRepository;
import vn.edu.topicregistration.shared.error.ApplicationErrorCode;
import vn.edu.topicregistration.shared.error.ApplicationException;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy.ActorIdentity;

class PeriodServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant LECTURER_START = Instant.parse("2026-09-10T01:00:00Z");
    private static final Instant LECTURER_END = Instant.parse("2026-09-10T03:00:00Z");
    private static final Instant STUDENT_START = Instant.parse("2026-09-10T04:00:00Z");
    private static final Instant STUDENT_END = Instant.parse("2026-09-10T06:00:00Z");

    PeriodTransactionService transactions;
    RegistrationPeriodRepository periods;
    AuthorizationPolicy authorization;
    Clock clock;
    FacultyEntity faculty;
    ActorIdentity dean;
    PeriodService service;

    @BeforeEach
    void setUp() {
        transactions = mock(PeriodTransactionService.class);
        periods = mock(RegistrationPeriodRepository.class);
        authorization = mock(AuthorizationPolicy.class);
        clock = mock(Clock.class);
        faculty = mock(FacultyEntity.class);
        dean = new ActorIdentity(
                9L, Set.of(RoleCode.LECTURER, RoleCode.DEAN), 11L, 21L);
        when(faculty.getId()).thenReturn(21L);
        when(authorization.requireRole(RoleCode.DEAN)).thenReturn(dean);
        when(clock.instant()).thenReturn(NOW);
        service = new PeriodService(transactions, periods, authorization, clock);
    }

    @Test
    void createValidatesInsideTheTransactionAndReturnsDerivedState() {
        runCreateFactoryInsideTransaction();

        var result = service.create(configuration("  Đợt HK1  "));

        assertEquals("Đợt HK1", result.name());
        assertEquals(TopicType.COURSE, result.topicType());
        assertEquals(RegistrationState.BEFORE_LECTURER, result.state());
        assertEquals(21L, result.facultyId());
        verify(transactions).create(eq(21L), any());
    }

    @Test
    void invalidCreateBecomesAStableValidationErrorBeforeInsert() {
        runCreateFactoryInsideTransaction();

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> service.create(configuration("  ")));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
    }

    @Test
    void updateUsesLockedTimeAppliesAllowedExtensionAndFlushesVersion() {
        RegistrationPeriodEntity current = new RegistrationPeriodEntity(
                faculty,
                "Đợt HK1",
                "COURSE",
                LECTURER_START,
                LECTURER_END,
                STUDENT_START,
                STUDENT_END,
                null,
                null);
        Instant openingTime = LECTURER_START;
        when(transactions.mutate(eq(7L), eq(0L), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<LockedPeriodContext, Object> mutation = invocation.getArgument(2);
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

        var result = service.update(7L, 0L, extension);

        assertEquals(LECTURER_END.plusSeconds(60), result.lecturerEnd());
        assertEquals(STUDENT_END.plusSeconds(60), result.studentEnd());
        assertEquals(RegistrationState.LECTURER_OPEN, result.state());
        verify(periods).flush();
    }

    private void runCreateFactoryInsideTransaction() {
        when(transactions.create(eq(21L), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Function<CreationContext, RegistrationPeriodEntity> factory = invocation.getArgument(1);
            return factory.apply(new CreationContext(faculty, dean, NOW));
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
}

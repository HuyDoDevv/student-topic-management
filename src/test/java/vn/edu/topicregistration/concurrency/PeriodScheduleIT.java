package vn.edu.topicregistration.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.integration.FixtureSupport;
import vn.edu.topicregistration.integration.MySqlTestSupport;
import vn.edu.topicregistration.service.PeriodService;
import vn.edu.topicregistration.service.PeriodService.PeriodView;
import vn.edu.topicregistration.service.PeriodRules.PeriodConfiguration;
import vn.edu.topicregistration.service.PeriodRules.RegistrationState;
import vn.edu.topicregistration.service.PeriodRules.TopicType;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.config.TimeConfig;

@SpringBootTest
@ActiveProfiles("local")
@Import(PeriodScheduleIT.MutableClockConfiguration.class)
class PeriodScheduleIT {
    private static final Instant BEFORE_OPENING = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant LECTURER_START = Instant.parse("2026-09-10T00:00:01Z");
    private static final Instant LECTURER_END = Instant.parse("2026-09-10T02:00:00Z");
    private static final Instant STUDENT_START = Instant.parse("2026-09-10T03:00:00Z");
    private static final Instant STUDENT_END = Instant.parse("2026-09-10T05:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PeriodService periodService;

    @Autowired
    MutableClock clock;

    long facultyId;
    long departmentId;
    long periodId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeEach
    void resetDatabaseAndFixtures() {
        DATABASE.resetAndMigrate();
        clock.set(BEFORE_OPENING);
        FixtureSupport fixtures = new FixtureSupport(dataSource, clock);
        FixtureSupport.OrganizationScope scope = fixtures.createOrganization(
                "Khoa CNTT concurrency");
        facultyId = scope.facultyId();
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'Bộ môn concurrency')",
                facultyId);
        departmentId = jdbc.queryForObject(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = 'Bộ môn concurrency'",
                Long.class, facultyId);
        fixtures.createIdentity(
                "dean-schedule", "Trưởng khoa", "unusable-concurrency-hash", true,
                scope, Set.of(RoleCode.DEAN));
        periodId = insertPeriod();
    }

    @AfterEach
    void releaseResourcesAfterFailure() throws Exception {
        if (rowLock != null) {
            try {
                rowLock.rollback();
            } finally {
                rowLock.close();
                rowLock = null;
            }
        }
        if (executor != null) {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            executor = null;
        }
    }

    @Test
    void updateThatWaitsUntilOpeningRechecksTimeAndCannotChangeLockedFields() throws Exception {
        PeriodConfiguration renameRequest = configuration(
                "Tên không được ghi", LECTURER_END.plusSeconds(600), STUDENT_END.plusSeconds(600));

        Future<PeriodView> update = startUpdateWhileRowIsLocked(renameRequest);
        clock.set(LECTURER_START);
        releaseRowLock();

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> update.get(10, TimeUnit.SECONDS));
        ApplicationException applicationFailure = assertInstanceOf(
                ApplicationException.class, failure.getCause());
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, applicationFailure.code());
        assertEquals("Đợt ban đầu", periodName());
        assertEquals(LECTURER_END, lecturerEnd());
        assertEquals(0L, periodVersion());
    }

    @Test
    void updateThatWaitsUntilOpeningCanStillExtendUnexpiredEndTimes() throws Exception {
        Instant extendedLecturerEnd = LECTURER_END.plusSeconds(600);
        Instant extendedStudentEnd = STUDENT_END.plusSeconds(600);
        PeriodConfiguration extension = configuration(
                "Đợt ban đầu", extendedLecturerEnd, extendedStudentEnd);

        Future<PeriodView> update = startUpdateWhileRowIsLocked(extension);
        clock.set(LECTURER_START);
        releaseRowLock();

        PeriodView result = update.get(10, TimeUnit.SECONDS);
        assertEquals(RegistrationState.LECTURER_OPEN, result.state());
        assertEquals(extendedLecturerEnd, lecturerEnd());
        assertEquals(extendedStudentEnd, studentEnd());
        assertEquals(1L, periodVersion());
    }

    private Connection rowLock;
    private ExecutorService executor;

    private Future<PeriodView> startUpdateWhileRowIsLocked(PeriodConfiguration request)
            throws Exception {
        rowLock = dataSource.getConnection();
        rowLock.setAutoCommit(false);
        rowLock.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        try (var statement = rowLock.prepareStatement(
                "SELECT id FROM registration_periods WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, periodId);
            try (var result = statement.executeQuery()) {
                result.next();
            }
        }

        CountDownLatch started = new CountDownLatch(1);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<PeriodView> update = executor.submit(() -> {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "dean-schedule",
                    "unused",
                    List.of(new SimpleGrantedAuthority("ROLE_DEAN")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            started.countDown();
            try {
                return periodService.update(periodId, 0L, departmentId, request);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        if (!started.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Update worker did not start");
        }
        assertThrows(TimeoutException.class, () -> update.get(250, TimeUnit.MILLISECONDS));
        assertFalse(update.isDone(), "Update must still be waiting for the period row lock");
        return update;
    }

    private void releaseRowLock() throws Exception {
        try {
            rowLock.commit();
        } finally {
            rowLock.close();
            rowLock = null;
            executor.shutdown();
        }
    }

    private PeriodConfiguration configuration(
            String name,
            Instant lecturerEnd,
            Instant studentEnd) {
        return new PeriodConfiguration(
                name,
                TopicType.COURSE,
                LECTURER_START,
                lecturerEnd,
                STUDENT_START,
                studentEnd,
                null,
                null);
    }

    private long insertPeriod() {
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, department_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end,
                    reviewer_score_deadline, council_report_date, version)
                VALUES (?, ?, 'Đợt ban đầu', 'COURSE', ?, ?, ?, ?, NULL, NULL, 0)
                """,
                facultyId, departmentId,
                Timestamp.from(LECTURER_START), Timestamp.from(LECTURER_END),
                Timestamp.from(STUDENT_START), Timestamp.from(STUDENT_END));
        return jdbc.queryForObject("SELECT MAX(id) FROM registration_periods", Long.class);
    }

    private String periodName() {
        return jdbc.queryForObject(
                "SELECT name FROM registration_periods WHERE id = ?",
                String.class,
                periodId);
    }

    private Instant lecturerEnd() {
        return jdbc.queryForObject(
                "SELECT lecturer_end FROM registration_periods WHERE id = ?",
                (result, row) -> result.getTimestamp(1).toInstant(),
                periodId);
    }

    private Instant studentEnd() {
        return jdbc.queryForObject(
                "SELECT student_end FROM registration_periods WHERE id = ?",
                (result, row) -> result.getTimestamp(1).toInstant(),
                periodId);
    }

    private long periodVersion() {
        return jdbc.queryForObject(
                "SELECT version FROM registration_periods WHERE id = ?",
                Long.class,
                periodId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableClockConfiguration {
        @Bean
        @Primary
        MutableClock periodScheduleClock() {
            return new MutableClock(BEFORE_OPENING, TimeConfig.BUSINESS_ZONE);
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;

        MutableClock(Instant initial, ZoneId zone) {
            this.current = new AtomicReference<>(initial);
            this.zone = zone;
        }

        void set(Instant value) {
            current.set(value);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId targetZone) {
            return new MutableClock(instant(), targetZone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}

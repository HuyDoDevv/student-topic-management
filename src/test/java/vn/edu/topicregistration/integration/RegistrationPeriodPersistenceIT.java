package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import vn.edu.topicregistration.identity.persistence.FacultyEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodRepository;

@SpringBootTest
@ActiveProfiles("local")
class RegistrationPeriodPersistenceIT {
    private static final Instant LECTURER_START = Instant.parse("2026-09-10T01:00:00Z");
    private static final Instant LECTURER_END = Instant.parse("2026-09-10T03:00:00Z");
    private static final Instant STUDENT_START = Instant.parse("2026-09-10T04:00:00Z");
    private static final Instant STUDENT_END = Instant.parse("2026-09-10T06:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    RegistrationPeriodRepository periods;

    @Autowired
    EntityManager entityManager;

    @Autowired
    DataSource dataSource;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeAll
    static void migrateIsolatedDatabase() {
        DATABASE.resetAndMigrate();
    }

    @Test
    @Transactional
    void entityMappingPersistsAndQueriesByFaculty() {
        FacultyEntity faculty = new FacultyEntity("Khoa CNTT persistence");
        entityManager.persist(faculty);
        RegistrationPeriodEntity saved = periods.saveAndFlush(new RegistrationPeriodEntity(
                faculty,
                "Đợt KLTN",
                "KLTN",
                LECTURER_START,
                LECTURER_END,
                STUDENT_START,
                STUDENT_END,
                LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 25)));
        entityManager.clear();

        RegistrationPeriodEntity loaded = periods
                .findByIdAndFacultyId(saved.getId(), faculty.getId())
                .orElseThrow();

        assertEquals("Đợt KLTN", loaded.getName());
        assertEquals("KLTN", loaded.getTopicType());
        assertEquals(LECTURER_START, loaded.getLecturerStart());
        assertEquals(LocalDate.of(2026, 9, 20), loaded.getReviewerScoreDeadline());
        assertEquals(1, periods.findAllByFacultyIdOrderByLecturerStartDesc(faculty.getId()).size());
    }

    @Test
    @Transactional
    void repositoryAcquiresPessimisticWriteLock() {
        FacultyEntity faculty = new FacultyEntity("Khoa lock");
        entityManager.persist(faculty);
        RegistrationPeriodEntity saved = periods.saveAndFlush(new RegistrationPeriodEntity(
                faculty,
                "Đợt khóa",
                "COURSE",
                LECTURER_START,
                LECTURER_END,
                STUDENT_START,
                STUDENT_END,
                null,
                null));
        entityManager.clear();

        RegistrationPeriodEntity locked = periods.findByIdForUpdate(saved.getId()).orElseThrow();

        assertEquals(LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(locked));
    }

    @Test
    void databaseRejectsMissingOrInapplicableConditionalDates() {
        long facultyId = insertFaculty("Khoa constraint");

        assertThrows(SQLException.class,
                () -> insertRawPeriod(facultyId, "TLCN", null, null));
        assertThrows(SQLException.class,
                () -> insertRawPeriod(
                        facultyId, "KLTN", LocalDate.of(2026, 9, 20), null));
        assertThrows(SQLException.class,
                () -> insertRawPeriod(
                        facultyId, "COURSE", LocalDate.of(2026, 9, 20), null));
    }

    private long insertFaculty(String name) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO faculties(name) VALUES (?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not create faculty fixture", exception);
        }
    }

    private void insertRawPeriod(
            long facultyId,
            String topicType,
            LocalDate reviewerDeadline,
            LocalDate councilDate) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO registration_periods(
                            faculty_id, name, topic_type,
                            lecturer_start, lecturer_end, student_start, student_end,
                            reviewer_score_deadline, council_report_date)
                        VALUES (?, 'Constraint test', ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            statement.setLong(1, facultyId);
            statement.setString(2, topicType);
            statement.setTimestamp(3, Timestamp.from(LECTURER_START));
            statement.setTimestamp(4, Timestamp.from(LECTURER_END));
            statement.setTimestamp(5, Timestamp.from(STUDENT_START));
            statement.setTimestamp(6, Timestamp.from(STUDENT_END));
            statement.setObject(7, reviewerDeadline);
            statement.setObject(8, councilDate);
            statement.executeUpdate();
        }
    }
}

package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.TopicRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;

@SpringBootTest
@ActiveProfiles("local")
class DepartmentPersistenceIT {
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();
    private static final Instant LECTURER_START = Instant.parse("2026-09-10T01:00:00Z");
    private static final Instant LECTURER_END = Instant.parse("2026-09-10T03:00:00Z");
    private static final Instant STUDENT_START = Instant.parse("2026-09-10T04:00:00Z");
    private static final Instant STUDENT_END = Instant.parse("2026-09-10T06:00:00Z");

    @Autowired
    DepartmentRepository departments;

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    TopicRepository topics;

    @Autowired
    EntityManager entityManager;

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
    void repositoryPersistsAndQueriesDepartmentsWithinFaculty() {
        FacultyEntity primaryFaculty = new FacultyEntity("Khoa persistence A");
        FacultyEntity otherFaculty = new FacultyEntity("Khoa persistence B");
        entityManager.persist(primaryFaculty);
        entityManager.persist(otherFaculty);
        DepartmentEntity networks = departments.save(
                new DepartmentEntity(primaryFaculty, "Mạng máy tính"));
        DepartmentEntity software = departments.save(
                new DepartmentEntity(primaryFaculty, "Công nghệ phần mềm"));
        departments.saveAndFlush(new DepartmentEntity(otherFaculty, "Công nghệ phần mềm"));
        entityManager.clear();

        List<DepartmentEntity> loaded = departments
                .findAllByFacultyIdOrderByNameAsc(primaryFaculty.getId());

        assertEquals(List.of("Công nghệ phần mềm", "Mạng máy tính"),
                loaded.stream().map(DepartmentEntity::getName).toList());
        assertEquals(networks.getId(), departments
                .findByIdAndFacultyId(networks.getId(), primaryFaculty.getId())
                .orElseThrow()
                .getId());
        assertFalse(departments
                .findByIdAndFacultyId(software.getId(), otherFaculty.getId())
                .isPresent());
        assertTrue(departments.existsByFacultyIdAndNameIgnoreCase(
                primaryFaculty.getId(), "cÔng NGHỆ PHẦN MỀM"));
    }

    @Test
    @Transactional
    void accountAndTopicMappingsPersistDepartment() {
        FacultyEntity faculty = new FacultyEntity("Khoa mapping Department");
        entityManager.persist(faculty);
        DepartmentEntity department = departments.saveAndFlush(
                new DepartmentEntity(faculty, "Hệ thống thông tin"));
        UserAccountEntity author = accounts.saveAndFlush(new UserAccountEntity(
                "Giảng viên Department",
                "department.lecturer",
                "NOT_A_LOGIN_HASH",
                null,
                faculty,
                department));
        RegistrationPeriodEntity period = period(faculty, "Đợt mapping Department");
        entityManager.persist(period);
        TopicEntity topic = topics.saveAndFlush(new TopicEntity(period, author, department));
        entityManager.clear();

        UserAccountEntity loadedAccount = accounts.findById(author.getId()).orElseThrow();
        TopicEntity loadedTopic = topics.findById(topic.getId()).orElseThrow();

        assertEquals(department.getId(), loadedAccount.getDepartment().getId());
        assertEquals(department.getId(), loadedTopic.getDepartment().getId());
        assertEquals(faculty.getId(), loadedTopic.getDepartment().getFaculty().getId());
    }

    @Test
    @Transactional
    void legacyRowsWithNullDepartmentRemainLoadable() {
        FacultyEntity faculty = new FacultyEntity("Khoa legacy Department");
        entityManager.persist(faculty);
        UserAccountEntity author = accounts.saveAndFlush(new UserAccountEntity(
                "Giảng viên legacy",
                "legacy.department.lecturer",
                "NOT_A_LOGIN_HASH",
                faculty));
        RegistrationPeriodEntity period = period(faculty, "Đợt legacy Department");
        entityManager.persist(period);
        TopicEntity topic = topics.saveAndFlush(new TopicEntity(period, author));
        entityManager.clear();

        assertNull(accounts.findById(author.getId()).orElseThrow().getDepartment());
        assertNull(topics.findById(topic.getId()).orElseThrow().getDepartment());
    }

    private static RegistrationPeriodEntity period(FacultyEntity faculty, String name) {
        return new RegistrationPeriodEntity(
                faculty,
                name,
                "COURSE",
                LECTURER_START,
                LECTURER_END,
                STUDENT_START,
                STUDENT_END,
                null,
                null);
    }
}

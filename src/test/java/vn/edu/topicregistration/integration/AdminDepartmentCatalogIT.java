package vn.edu.topicregistration.integration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.time.Clock;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.config.TimeConfig;
import vn.edu.topicregistration.entity.RoleCode;

@SpringBootTest
@ActiveProfiles("local")
class AdminDepartmentCatalogIT {
    private static final String UNUSED_HASH = "unusable-department-catalog-hash";
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    WebApplicationContext context;

    @Autowired
    DataSource dataSource;

    MockMvc mvc;
    JdbcTemplate jdbc;
    FixtureSupport fixtures;
    FixtureSupport.OrganizationScope faculty;
    FixtureSupport.OrganizationScope otherFaculty;
    FixtureSupport.IdentityFixture admin;
    FixtureSupport.IdentityFixture lecturer;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeEach
    void resetAndSeed() {
        DATABASE.resetAndMigrate();
        jdbc = new JdbcTemplate(dataSource);
        fixtures = new FixtureSupport(dataSource, Clock.system(TimeConfig.BUSINESS_ZONE));
        faculty = fixtures.createOrganization("Khoa Công nghệ Thông tin catalog");
        otherFaculty = fixtures.createOrganization("Khoa Cơ khí catalog");
        admin = fixtures.createIdentity(
                "catalog-admin", "Quản trị catalog", UNUSED_HASH, true, null,
                Set.of(RoleCode.ADMIN));
        lecturer = fixtures.createIdentity(
                "catalog-lecturer", "Giảng viên catalog", UNUSED_HASH, true, faculty,
                Set.of(RoleCode.LECTURER));
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void adminCreatesSearchesAndRenamesDepartment() throws Exception {
        mvc.perform(post("/admin/departments")
                        .param("facultyId", Long.toString(faculty.facultyId()))
                        .param("name", "  Hệ thống thông tin  ")
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/departments"));
        long departmentId = departmentId(faculty.facultyId(), "Hệ thống thông tin");

        mvc.perform(get("/admin/departments")
                        .param("query", "hệ thống")
                        .param("facultyId", Long.toString(faculty.facultyId()))
                        .with(actor(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hệ thống thông tin")))
                .andExpect(content().string(containsString(
                        "Khoa Công nghệ Thông tin catalog")));

        mvc.perform(post("/admin/departments/{id}", departmentId)
                        .param("facultyId", Long.toString(faculty.facultyId()))
                        .param("name", "Khoa học dữ liệu")
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isSeeOther());

        assertEquals("Khoa học dữ liệu", jdbc.queryForObject(
                "SELECT name FROM departments WHERE id = ?", String.class, departmentId));
    }

    @Test
    void duplicateIsRejectedPerFacultyButAllowedAcrossFaculties() throws Exception {
        createDepartment(faculty.facultyId(), "Công nghệ phần mềm");

        mvc.perform(post("/admin/departments")
                        .param("facultyId", Long.toString(faculty.facultyId()))
                        .param("name", "cÔng NGHỆ PHẦN MỀM")
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/admin/departments")
                        .param("facultyId", Long.toString(otherFaculty.facultyId()))
                        .param("name", "Công nghệ phần mềm")
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isSeeOther());

        assertEquals(1L, countDepartments(faculty.facultyId(), "Công nghệ phần mềm"));
        assertEquals(1L, countDepartments(otherFaculty.facultyId(), "Công nghệ phần mềm"));
    }

    @Test
    void csrfAndPersistedAdminRoleProtectEveryMutation() throws Exception {
        mvc.perform(post("/admin/departments")
                        .param("facultyId", Long.toString(faculty.facultyId()))
                        .param("name", "Không có CSRF")
                        .with(actor(admin, "ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/departments")
                        .with(actor(lecturer, "LECTURER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/departments")
                        .param("facultyId", Long.toString(faculty.facultyId()))
                        .param("name", "Không phải ADMIN")
                        .with(actor(lecturer, "ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());

        assertEquals(0L, countAllDepartments());
    }

    @Test
    void noDeleteRouteExistsAndForeignKeysProtectReferencedDepartment() throws Exception {
        createDepartment(faculty.facultyId(), "Mạng máy tính");
        long departmentId = departmentId(faculty.facultyId(), "Mạng máy tính");
        jdbc.update("UPDATE user_accounts SET department_id = ? WHERE id = ?",
                departmentId, lecturer.accountId());

        mvc.perform(post("/admin/departments/{id}/delete", departmentId)
                        .with(actor(admin, "ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM departments WHERE id = ?", departmentId));
        assertEquals(1L, countAllDepartments());
    }

    private void createDepartment(long facultyId, String name) {
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, ?)", facultyId, name);
    }

    private long departmentId(long facultyId, String name) {
        return jdbc.queryForObject(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = ?",
                Long.class, facultyId, name);
    }

    private long countDepartments(long facultyId, String name) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM departments WHERE faculty_id = ? AND name = ?",
                Long.class, facultyId, name);
    }

    private long countAllDepartments() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM departments", Long.class);
    }

    private static UserRequestPostProcessor actor(
            FixtureSupport.IdentityFixture identity, String... roles) {
        return user(identity.username()).roles(roles);
    }
}

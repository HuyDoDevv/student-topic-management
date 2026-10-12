package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.entity.StudentProfileEntity;
import vn.edu.topicregistration.repository.StudentProfileRepository;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.CreateStudentCommand;
import vn.edu.topicregistration.service.AdminAccountService.CreateLecturerCommand;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;

/** Verifies that student provisioning does not leave a partial account on profile failure. */
@SpringBootTest
@ActiveProfiles("local")
class AccountProvisioningIT {
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    AdminAccountService accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    WebApplicationContext context;

    @MockitoBean
    StudentProfileRepository studentProfiles;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeEach
    void resetDatabaseAndAuthenticateAdmin() {
        DATABASE.resetAndMigrate();
        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash)
                VALUES ('Quản trị rollback', 'rollback.admin', 'NOT_A_LOGIN_HASH')
                """);
        jdbc.update("""
                INSERT INTO user_roles(user_id, role_code)
                SELECT id, 'ADMIN' FROM user_accounts WHERE username = 'rollback.admin'
                """);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "rollback.admin", "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void profileFailureRollsBackAccountAndRoleAssignments() {
        when(studentProfiles.existsByStudentCodeIgnoreCase("23119999")).thenReturn(false);
        when(studentProfiles.saveAndFlush(any(StudentProfileEntity.class)))
                .thenThrow(new IllegalStateException("simulated profile failure"));

        assertThrows(IllegalStateException.class, () -> accounts.createStudent(
                new CreateStudentCommand(
                        "23119999", "Sinh viên rollback", "23110CL9", "K2026",
                        "temporary-password")));

        assertEquals(0L, count(
                "SELECT COUNT(*) FROM user_accounts WHERE username = '23119999'"));
        assertEquals(0L, count("""
                SELECT COUNT(*) FROM user_roles role_assignment
                JOIN user_accounts account ON account.id = role_assignment.user_id
                WHERE account.username = '23119999'
                """));
    }

    @Test
    void newlyProvisionedAccountsRequirePasswordChange() {
        long departmentId = addDepartment();
        accounts.createLecturer(new CreateLecturerCommand(
                "security.lecturer", "Giảng viên bảo mật", "temporary-password", departmentId));

        assertEquals(1L, count("""
                SELECT COUNT(*) FROM user_accounts
                WHERE username = 'security.lecturer'
                  AND must_change_password = TRUE
                  AND security_version = 0
                """));
    }

    @Test
    void staffCreationAndFirstAssignmentPersistValidatedDepartment() {
        long departmentId = addDepartment();
        accounts.createLecturer(new CreateLecturerCommand(
                "department.lecturer", "Giảng viên Bộ môn", "temporary-password", departmentId));
        assertEquals(departmentId, jdbc.queryForObject(
                "SELECT department_id FROM user_accounts WHERE username = 'department.lecturer'",
                Long.class));

        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                SELECT 'Giảng viên cũ', 'legacy.lecturer', 'NOT_A_LOGIN_HASH', id
                FROM faculties WHERE name = ?
                """, AdminAccountService.DEFAULT_FACULTY_NAME);
        jdbc.update("""
                INSERT INTO user_roles(user_id, role_code)
                SELECT id, 'LECTURER' FROM user_accounts WHERE username = 'legacy.lecturer'
                """);
        long accountId = jdbc.queryForObject(
                "SELECT id FROM user_accounts WHERE username = 'legacy.lecturer'", Long.class);
        accounts.assignStaffDepartment(accountId, departmentId);
        assertEquals(departmentId, jdbc.queryForObject(
                "SELECT department_id FROM user_accounts WHERE id = ?", Long.class, accountId));

        jdbc.update("INSERT INTO faculties(name) VALUES ('Khoa khác')");
        jdbc.update("""
                INSERT INTO departments(faculty_id, name)
                SELECT id, 'Bộ môn khác' FROM faculties WHERE name = 'Khoa khác'
                """);
        long otherDepartmentId = jdbc.queryForObject(
                "SELECT id FROM departments WHERE name = 'Bộ môn khác'", Long.class);
        ApplicationException crossFaculty = assertThrows(ApplicationException.class,
                () -> accounts.createLecturer(new CreateLecturerCommand(
                        "forged.lecturer", "Giảng viên giả mạo", "temporary-password",
                        otherDepartmentId)));
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, crossFaculty.code());
        assertEquals(0L, count("""
                SELECT COUNT(*) FROM user_accounts WHERE username = 'forged.lecturer'
                """));
        ApplicationException transfer = assertThrows(ApplicationException.class,
                () -> accounts.assignStaffDepartment(accountId, otherDepartmentId));
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, transfer.code());
        assertEquals(departmentId, jdbc.queryForObject(
                "SELECT department_id FROM user_accounts WHERE id = ?", Long.class, accountId));
    }

    @Test
    void staffFormRendersDepartmentAndPostRequiresCsrfAndPersistedAdmin() throws Exception {
        long departmentId = addDepartment();
        MockMvc mvc = webAppContextSetup(context).apply(springSecurity()).build();

        mvc.perform(get("/admin/accounts/lecturers/new")
                        .with(user("rollback.admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Công nghệ phần mềm")));
        mvc.perform(post("/admin/accounts/lecturers")
                        .with(user("rollback.admin").roles("ADMIN"))
                        .param("username", "csrf.lecturer")
                        .param("fullName", "Giảng viên CSRF")
                        .param("password", "temporary-password")
                        .param("departmentId", Long.toString(departmentId)))
                .andExpect(status().isForbidden());
        assertEquals(0L, count("SELECT COUNT(*) FROM user_accounts WHERE username = 'csrf.lecturer'"));

        jdbc.update("""
                INSERT INTO user_accounts(full_name, username, password_hash, faculty_id)
                SELECT 'Giảng viên khác', 'not.admin', 'NOT_A_LOGIN_HASH', id
                FROM faculties WHERE name = ?
                """, AdminAccountService.DEFAULT_FACULTY_NAME);
        jdbc.update("""
                INSERT INTO user_roles(user_id, role_code)
                SELECT id, 'LECTURER' FROM user_accounts WHERE username = 'not.admin'
                """);
        mvc.perform(post("/admin/accounts/lecturers")
                        .with(user("not.admin").roles("ADMIN")).with(csrf())
                        .param("username", "csrf.lecturer")
                        .param("fullName", "Giảng viên CSRF")
                        .param("password", "temporary-password")
                        .param("departmentId", Long.toString(departmentId)))
                .andExpect(status().isForbidden());
        assertEquals(0L, count("SELECT COUNT(*) FROM user_accounts WHERE username = 'csrf.lecturer'"));

        mvc.perform(post("/admin/accounts/lecturers")
                        .with(user("rollback.admin").roles("ADMIN")).with(csrf())
                        .param("username", "csrf.lecturer")
                        .param("fullName", "Giảng viên CSRF")
                        .param("password", "temporary-password")
                        .param("departmentId", Long.toString(departmentId)))
                .andExpect(status().isSeeOther());
        assertEquals(departmentId, jdbc.queryForObject(
                "SELECT department_id FROM user_accounts WHERE username = 'csrf.lecturer'",
                Long.class));
    }

    private long addDepartment() {
        jdbc.update("""
                INSERT INTO departments(faculty_id, name)
                SELECT id, 'Công nghệ phần mềm' FROM faculties WHERE name = ?
                """, AdminAccountService.DEFAULT_FACULTY_NAME);
        return jdbc.queryForObject(
                "SELECT id FROM departments WHERE name = 'Công nghệ phần mềm'", Long.class);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}

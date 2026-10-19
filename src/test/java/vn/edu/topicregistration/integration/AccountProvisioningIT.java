package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.repository.StudentProfileRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.CreateStudentCommand;
import vn.edu.topicregistration.service.AdminAccountService.CreateLecturerCommand;
import vn.edu.topicregistration.service.AdminAccountService.CreateDeanCommand;
import vn.edu.topicregistration.service.PeriodService;
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
    PeriodService periods;

    @Autowired
    UserAccountRepository userAccounts;

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
    void lecturerPersistsValidatedDepartmentWhileDeanHasNone() {
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

        accounts.createDean(new CreateDeanCommand(
                "department.dean", "Trưởng khoa", "temporary-password"));
        long deanId = accountId("department.dean");
        assertEquals(null, jdbc.queryForObject(
                "SELECT department_id FROM user_accounts WHERE id = ?", Long.class, deanId));
        ApplicationException deanAssignment = assertThrows(ApplicationException.class,
                () -> accounts.assignStaffDepartment(deanId, departmentId));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, deanAssignment.code());

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

    @Test
    void secondActiveDeanIsRejectedAndTransferPreservesOldPeriods() {
        long departmentId = addDepartment();
        accounts.createDean(new CreateDeanCommand(
                "dean.old", "Trưởng khoa cũ", "temporary-password"));
        long oldDeanId = accountId("dean.old");
        assertEquals(List.of("DEAN"), jdbc.queryForList("""
                SELECT role_code FROM user_roles WHERE user_id = ? ORDER BY role_code
                """, String.class, oldDeanId));
        jdbc.update("""
                INSERT INTO registration_periods
                    (faculty_id, department_id, name, topic_type,
                     lecturer_start, lecturer_end, student_start, student_end)
                SELECT id, ?, 'Đợt cũ', 'COURSE',
                       '2027-01-01 00:00:00', '2027-01-10 00:00:00',
                       '2027-01-11 00:00:00', '2027-01-20 00:00:00'
                FROM faculties WHERE name = ?
                """, departmentId, AdminAccountService.DEFAULT_FACULTY_NAME);
        long periodId = jdbc.queryForObject(
                "SELECT id FROM registration_periods WHERE name = 'Đợt cũ'", Long.class);

        ApplicationException duplicate = assertThrows(ApplicationException.class,
                () -> accounts.createDean(new CreateDeanCommand(
                        "dean.blocked", "Trưởng khoa trùng", "temporary-password")));
        assertEquals(ApplicationErrorCode.DEAN_ALREADY_ACTIVE, duplicate.code());
        assertEquals(0L, count("SELECT COUNT(*) FROM user_accounts WHERE username = 'dean.blocked'"));

        accounts.lockAccount(oldDeanId);
        accounts.createDean(new CreateDeanCommand(
                "dean.new", "Trưởng khoa mới", "temporary-password"));
        ApplicationException cannotReenable = assertThrows(ApplicationException.class,
                () -> accounts.unlockAccount(oldDeanId));
        assertEquals(ApplicationErrorCode.DEAN_ALREADY_ACTIVE, cannotReenable.code());
        assertEquals(1L, activeDeanCount());
        assertEquals(1L, count("SELECT COUNT(*) FROM user_accounts WHERE username = 'dean.old' AND enabled = FALSE"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dean.new", "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_DEAN"))));
        assertTrue(periods.listForCurrentDean().stream().anyMatch(period -> period.id() == periodId));
        assertEquals(1L, count("SELECT COUNT(*) FROM registration_periods WHERE name = 'Đợt cũ'"));
    }

    @Test
    void concurrentDeanCreationsLeaveOnlyOneEnabledDean() throws Exception {
        assertOneConcurrentSuccess(
                () -> accounts.createDean(new CreateDeanCommand(
                        "dean.race.one", "Trưởng khoa Một", "temporary-password")),
                () -> accounts.createDean(new CreateDeanCommand(
                        "dean.race.two", "Trưởng khoa Hai", "temporary-password")));
        assertEquals(1L, activeDeanCount());
    }

    @Test
    void concurrentDeanUnlocksLeaveOnlyOneEnabledDean() throws Exception {
        accounts.createDean(new CreateDeanCommand(
                "dean.locked.one", "Trưởng khoa Một", "temporary-password"));
        long firstId = accountId("dean.locked.one");
        accounts.lockAccount(firstId);
        accounts.createDean(new CreateDeanCommand(
                "dean.locked.two", "Trưởng khoa Hai", "temporary-password"));
        long secondId = accountId("dean.locked.two");
        accounts.lockAccount(secondId);

        assertOneConcurrentSuccess(
                () -> accounts.unlockAccount(firstId),
                () -> accounts.unlockAccount(secondId));
        assertEquals(1L, activeDeanCount());
    }

    @Test
    void concurrentDeanCreateAndUnlockLeaveOnlyOneEnabledDean() throws Exception {
        accounts.createDean(new CreateDeanCommand(
                "dean.locked", "Trưởng khoa cũ", "temporary-password"));
        long lockedId = accountId("dean.locked");
        accounts.lockAccount(lockedId);

        assertOneConcurrentSuccess(
                () -> accounts.unlockAccount(lockedId),
                () -> accounts.createDean(new CreateDeanCommand(
                        "dean.created", "Trưởng khoa mới", "temporary-password")));
        assertEquals(1L, activeDeanCount());
    }

    @Test
    void lecturerCandidateQueriesExcludeDeanEvenWithLegacyMixedRoles() {
        long departmentId = addDepartment();
        accounts.createDean(new CreateDeanCommand(
                "dean.candidate", "Trưởng khoa", "temporary-password"));
        long deanId = accountId("dean.candidate");
        accounts.createLecturer(new CreateLecturerCommand(
                "lecturer.candidate", "Giảng viên", "temporary-password", departmentId));
        long lecturerId = accountId("lecturer.candidate");
        long facultyId = jdbc.queryForObject(
                "SELECT faculty_id FROM user_accounts WHERE id = ?", Long.class, deanId);

        assertEquals(List.of(lecturerId), userAccounts.findEnabledByFacultyAndRole(
                facultyId, RoleCode.LECTURER).stream().map(account -> account.getId()).toList());
        jdbc.update("INSERT INTO user_roles(user_id, role_code) VALUES (?, 'LECTURER')", deanId);
        assertEquals(List.of(lecturerId), userAccounts.findEnabledByFacultyAndRole(
                facultyId, RoleCode.LECTURER).stream().map(account -> account.getId()).toList());
        assertEquals(List.of(lecturerId), userAccounts.findEnabledByFacultyAndDepartmentAndRole(
                facultyId, departmentId, RoleCode.LECTURER).stream()
                .map(account -> account.getId()).toList());
    }

    private void assertOneConcurrentSuccess(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> outcomes = new ArrayList<>();
            for (Callable<?> operation : List.of(first, second)) {
                outcomes.add(executor.submit(() -> {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    "rollback.admin", "N/A",
                                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                    try {
                        ready.countDown();
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Concurrent test did not start");
                        }
                        try {
                            operation.call();
                            return true;
                        } catch (ApplicationException rejected) {
                            assertEquals(ApplicationErrorCode.DEAN_ALREADY_ACTIVE, rejected.code());
                            return false;
                        }
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> outcome : outcomes) {
                if (outcome.get(30, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private long accountId(String username) {
        return jdbc.queryForObject(
                "SELECT id FROM user_accounts WHERE username = ?", Long.class, username);
    }

    private long activeDeanCount() {
        return count("""
                SELECT COUNT(*) FROM user_accounts account
                JOIN user_roles assignment ON assignment.user_id = account.id
                JOIN faculties faculty ON faculty.id = account.faculty_id
                WHERE assignment.role_code = 'DEAN' AND account.enabled = TRUE
                  AND faculty.name = 'Khoa Công nghệ Thông tin'
                """);
    }

    private long addDepartment() {
        jdbc.update("""
                INSERT INTO departments(faculty_id, name)
                SELECT id, 'Công nghệ phần mềm' FROM faculties WHERE name = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM departments department
                      WHERE department.faculty_id = faculties.id
                        AND department.name = 'Công nghệ phần mềm')
                """, AdminAccountService.DEFAULT_FACULTY_NAME);
        return jdbc.queryForObject(
                "SELECT id FROM departments WHERE name = 'Công nghệ phần mềm'", Long.class);
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}

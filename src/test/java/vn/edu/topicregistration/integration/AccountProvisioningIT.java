package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

import vn.edu.topicregistration.entity.StudentProfileEntity;
import vn.edu.topicregistration.repository.StudentProfileRepository;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.CreateStudentCommand;

/** Verifies that student provisioning does not leave a partial account on profile failure. */
@SpringBootTest
@ActiveProfiles("local")
class AccountProvisioningIT {
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    AdminAccountService accounts;

    @Autowired
    JdbcTemplate jdbc;

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

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}

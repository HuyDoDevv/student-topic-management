package vn.edu.topicregistration.integration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.repository.UserAccountRepository;

/** Verifies self-service email through real login, authorization, templates and MySQL constraints. */
@SpringBootTest
@ActiveProfiles("local")
class AccountEmailHttpIT {
    private static final String PASSWORD = "email-browser-test-only";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    private static final MySqlTestSupport DATABASE = preparedDatabase();

    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean UserAccountRepository accounts;

    private MockMvc mvc;

    private static MySqlTestSupport preparedDatabase() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        return database;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
        registry.add("demo.seed.enabled", () -> false);
    }

    @BeforeEach
    void prepareAccountsAndHttp() {
        DATABASE.resetAndMigrate();
        FixtureSupport fixtures = new FixtureSupport(DATABASE.dataSource(), Clock.systemUTC());
        var faculty = fixtures.createOrganization("Khoa kiểm tra email");
        for (RoleCode role : RoleCode.values()) {
            fixtures.createIdentity(username(role), "Người dùng " + role, PASSWORD_HASH, true,
                    role == RoleCode.ADMIN ? null : faculty, Set.of(role));
        }
        jdbc.update("UPDATE user_accounts SET email = 'student@example.test' WHERE username = ?",
                username(RoleCode.STUDENT));
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"DEAN", "LECTURER"})
    void staffUpdatesOwnEmailAndContinuesUsingSameSession(RoleCode role) throws Exception {
        MockHttpSession session = login(role);
        mvc.perform(get("/account/email").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa cập nhật")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("href=\"/account/email\"")));

        mvc.perform(post("/account/email").session(session).with(csrf())
                        .param("email", "  " + role.name() + "@Example.TEST  ")
                        .param("accountId", Long.toString(accountId(RoleCode.STUDENT)))
                        .param("username", "forged")
                        .param("roles", "ADMIN"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/account/email"));

        String expected = role.name().toLowerCase(java.util.Locale.ROOT) + "@example.test";
        mvc.perform(get("/account/email").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đã cập nhật email của bạn.")))
                .andExpect(content().string(containsString("value=\"" + expected + "\"")));
        mvc.perform(get("/account/email").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(expected)));

        assertEquals(expected, email(role));
        assertEquals("student@example.test", email(RoleCode.STUDENT));
        assertEquals(PASSWORD_HASH, jdbc.queryForObject(
                "SELECT password_hash FROM user_accounts WHERE username = ?", String.class, username(role)));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT security_version FROM user_accounts WHERE username = ?", Long.class, username(role)));
        assertEquals(role.name(), jdbc.queryForObject(
                "SELECT role_code FROM user_roles WHERE user_id = ?", String.class, accountId(role)));

        mvc.perform(post("/account/email").session(session).with(csrf())
                        .param("email", expected.toUpperCase(java.util.Locale.ROOT)))
                .andExpect(status().isSeeOther());
        assertEquals(expected, email(role));
    }

    @Test
    void invalidAndDuplicateEmailsRenderFieldErrorsAndLeaveEmailUnchanged() throws Exception {
        MockHttpSession session = login(RoleCode.DEAN);
        jdbc.update("UPDATE user_accounts SET email = 'original@example.test' WHERE username = ?",
                username(RoleCode.DEAN));
        for (String invalid : List.of("", "   ", "not-an-email", "a".repeat(250) + "@example.test",
                "STUDENT@example.test")) {
            mvc.perform(post("/account/email").session(session).with(csrf()).param("email", invalid))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(model().attributeHasFieldErrors("changeEmailForm", "email"))
                    .andExpect(content().string(containsString("original@example.test")));
            assertEquals("original@example.test", email(RoleCode.DEAN));
        }
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"ADMIN", "STUDENT"})
    void otherRolesCannotReadOrUpdateEvenWithInvalidInput(RoleCode role) throws Exception {
        MockHttpSession session = login(role);
        String before = email(role);
        mvc.perform(get("/account/password").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/account/email\""))));
        mvc.perform(get("/account/email").session(session)).andExpect(status().isForbidden());
        for (String input : List.of("valid@example.test", "invalid")) {
            mvc.perform(post("/account/email").session(session).with(csrf()).param("email", input))
                    .andExpect(status().isForbidden());
        }
        assertEquals(before, email(role));
    }

    @Test
    void requiresLoginAndCsrf() throws Exception {
        mvc.perform(get("/account/email"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/account/email").with(csrf()).param("email", "valid@example.test"))
                .andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/account/email").session(login(RoleCode.DEAN))
                        .param("email", "valid@example.test"))
                .andExpect(status().isForbidden());
        assertNull(email(RoleCode.DEAN));
    }

    @Test
    void concurrentAccountsChoosingSameEmailGetOneSuccessAndOneInlineDuplicateError() throws Exception {
        MockHttpSession dean = login(RoleCode.DEAN);
        MockHttpSession lecturer = login(RoleCode.LECTURER);
        CyclicBarrier bothChecked = new CyclicBarrier(2);
        doAnswer(invocation -> {
            boolean exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_accounts WHERE LOWER(email) = ?",
                    Long.class, "shared@example.test") > 0;
            bothChecked.await(10, TimeUnit.SECONDS);
            return exists;
        }).when(accounts).existsByEmailIgnoreCase("shared@example.test");

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> mvc.perform(post("/account/email")
                    .session(dean).with(csrf()).param("email", "shared@example.test")).andReturn());
            var second = executor.submit(() -> mvc.perform(post("/account/email")
                    .session(lecturer).with(csrf()).param("email", "shared@example.test")).andReturn());
            var outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(List.of(303, 422), outcomes.stream()
                    .map(result -> result.getResponse().getStatus()).sorted().toList());
            var rejected = outcomes.stream().filter(result -> result.getResponse().getStatus() == 422)
                    .findFirst().orElseThrow();
            assertNull(rejected.getResolvedException(), "Duplicate should be rendered as a form error");
            org.junit.jupiter.api.Assertions.assertTrue(rejected.getResponse().getContentAsString()
                    .contains("Email này đã được sử dụng."));
        }
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_accounts WHERE email = 'shared@example.test'", Long.class));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_accounts
                WHERE username IN ('email.dean', 'email.lecturer') AND email IS NULL
                """, Long.class));
    }

    private MockHttpSession login(RoleCode role) throws Exception {
        var result = mvc.perform(post("/login").with(csrf())
                        .param("username", username(role)).param("password", PASSWORD))
                .andExpect(status().isFound()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String username(RoleCode role) {
        return "email." + role.name().toLowerCase(java.util.Locale.ROOT);
    }

    private long accountId(RoleCode role) {
        return jdbc.queryForObject("SELECT id FROM user_accounts WHERE username = ?",
                Long.class, username(role));
    }

    private String email(RoleCode role) {
        return jdbc.queryForObject("SELECT email FROM user_accounts WHERE username = ?",
                String.class, username(role));
    }
}

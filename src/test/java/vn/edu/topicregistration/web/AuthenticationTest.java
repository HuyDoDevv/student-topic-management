package vn.edu.topicregistration.web;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@ActiveProfiles("test")
class AuthenticationTest {
    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @MockitoBean
    UserAccountRepository accounts;

    @BeforeEach
    void setUpAccounts() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        UserAccountEntity lecturer = account("lecturer", true, Set.of(RoleCode.LECTURER));
        UserAccountEntity student = account("student", true, Set.of(RoleCode.STUDENT));
        UserAccountEntity disabled = account("disabled", false, Set.of(RoleCode.LECTURER));
        UserAccountEntity invalidCombination = account("invalid-combination", true,
                Set.of(RoleCode.STUDENT, RoleCode.LECTURER));
        UserAccountEntity studentAdmin = account("student-admin", true,
                Set.of(RoleCode.STUDENT, RoleCode.ADMIN));
        UserAccountEntity studentDean = account("student-dean", true,
                Set.of(RoleCode.STUDENT, RoleCode.DEAN));
        UserAccountEntity lecturerDean = account("lecturer-dean", true,
                Set.of(RoleCode.LECTURER, RoleCode.DEAN));
        when(accounts.findByUsername("lecturer")).thenReturn(Optional.of(lecturer));
        when(accounts.findByUsername("student")).thenReturn(Optional.of(student));
        when(accounts.findByUsername("disabled")).thenReturn(Optional.of(disabled));
        when(accounts.findByUsername("invalid-combination")).thenReturn(Optional.of(invalidCombination));
        when(accounts.findByUsername("student-admin")).thenReturn(Optional.of(studentAdmin));
        when(accounts.findByUsername("student-dean")).thenReturn(Optional.of(studentDean));
        when(accounts.findByUsername("lecturer-dean")).thenReturn(Optional.of(lecturerDean));
    }

    @Test
    void loginPageIsPublicButApplicationRequiresAuthentication() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đăng nhập")));

        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void assignedAccountCanLoginAndReceivesOnlyPersistedRoles() throws Exception {
        mvc.perform(formLogin().user("lecturer").password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("lecturer").withRoles("LECTURER"));

        mvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "student")
                        .param("password", PASSWORD)
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername("student").withRoles("STUDENT"));

        mvc.perform(formLogin().user("lecturer-dean").password(PASSWORD))
                .andExpect(authenticated().withUsername("lecturer-dean")
                        .withRoles("LECTURER", "DEAN"));
    }

    @Test
    void invalidUnknownDisabledAndInvalidRoleCombinationCannotLogin() throws Exception {
        mvc.perform(formLogin().user("lecturer").password("wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());

        mvc.perform(formLogin().user("unknown").password(PASSWORD))
                .andExpect(unauthenticated());
        mvc.perform(formLogin().user("disabled").password(PASSWORD))
                .andExpect(unauthenticated());
        mvc.perform(formLogin().user("invalid-combination").password(PASSWORD))
                .andExpect(unauthenticated());
        mvc.perform(formLogin().user("student-admin").password(PASSWORD))
                .andExpect(unauthenticated());
        mvc.perform(formLogin().user("student-dean").password(PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void logoutRequiresCsrfAndSelfRegistrationDoesNotExist() throws Exception {
        mvc.perform(post("/logout").with(user("lecturer").roles("LECTURER")))
                .andExpect(status().isForbidden());

        mvc.perform(logout())
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        mvc.perform(post("/register")
                        .with(user("student").roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void successfulLoginChangesTheExistingSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String sessionIdBeforeLogin = session.getId();

        MvcResult result = mvc.perform(post("/login")
                        .session(session)
                        .with(request -> {
                            request.setRequestedSessionId(sessionIdBeforeLogin);
                            request.setRequestedSessionIdValid(true);
                            return request;
                        })
                        .with(csrf())
                        .param("username", "lecturer")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated())
                .andReturn();

        assertNotEquals(sessionIdBeforeLogin, result.getRequest().getSession(false).getId());
    }

    private UserAccountEntity account(String username, boolean enabled, Set<RoleCode> roles) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(1L);
        when(account.getUsername()).thenReturn(username);
        when(account.getPasswordHash()).thenReturn(PASSWORD_HASH);
        when(account.getFullName()).thenReturn("Test User");
        when(account.isEnabled()).thenReturn(enabled);
        when(account.getRoleCodes()).thenReturn(roles);
        return account;
    }
}

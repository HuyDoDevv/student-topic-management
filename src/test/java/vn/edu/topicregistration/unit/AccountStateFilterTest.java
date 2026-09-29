package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AccountPrincipal;
import vn.edu.topicregistration.security.AccountStateFilter;

class AccountStateFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeMatchingAccountContinuesRequest() throws Exception {
        UserAccountEntity account = account();
        Dependencies dependencies = dependencies(account, 0);
        MockHttpServletRequest request = request("/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dependencies.filter().doFilter(request, response, dependencies.chain());

        verify(dependencies.chain()).doFilter(request, response);
    }

    @Test
    void disabledAccountExpiresSessionAndRedirectsToLogin() throws Exception {
        UserAccountEntity account = account();
        account.disable();
        Dependencies dependencies = dependencies(account, 0);
        MockHttpServletRequest request = request("/dashboard");
        MockHttpSession session = (MockHttpSession) request.getSession();
        MockHttpServletResponse response = new MockHttpServletResponse();

        dependencies.filter().doFilter(request, response, dependencies.chain());

        assertEquals("/login?sessionExpired", response.getRedirectedUrl());
        assertThrows(IllegalStateException.class, session::getCreationTime);
        verify(dependencies.chain(), never()).doFilter(request, response);
    }

    @Test
    void changedSecurityVersionExpiresOldSession() throws Exception {
        UserAccountEntity account = account();
        account.changePassword("new-encoded-password");
        Dependencies dependencies = dependencies(account, 0);
        MockHttpServletRequest request = request("/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        dependencies.filter().doFilter(request, response, dependencies.chain());

        assertEquals("/login?sessionExpired", response.getRedirectedUrl());
        verify(dependencies.chain(), never()).doFilter(request, response);
    }

    @Test
    void temporaryPasswordCanOnlyReachPasswordPage() throws Exception {
        UserAccountEntity account = account();
        account.markPasswordAsTemporary();
        Dependencies dependencies = dependencies(account, 0);
        MockHttpServletRequest dashboard = request("/dashboard");
        MockHttpServletResponse dashboardResponse = new MockHttpServletResponse();

        dependencies.filter().doFilter(
                dashboard, dashboardResponse, dependencies.chain());

        assertEquals("/account/password", dashboardResponse.getRedirectedUrl());
        verify(dependencies.chain(), never()).doFilter(dashboard, dashboardResponse);

        FilterChain allowedChain = mock(FilterChain.class);
        MockHttpServletRequest passwordPage = request("/account/password");
        MockHttpServletResponse passwordResponse = new MockHttpServletResponse();
        dependencies.filter().doFilter(passwordPage, passwordResponse, allowedChain);
        verify(allowedChain).doFilter(passwordPage, passwordResponse);
    }

    private static Dependencies dependencies(UserAccountEntity account, long sessionVersion) {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        when(accounts.findByIdAndUsername(7L, "23110001"))
                .thenReturn(Optional.of(account));
        FilterChain chain = mock(FilterChain.class);
        AccountPrincipal principal = new AccountPrincipal(
                7L, "23110001", "encoded", "Sinh viên Một", true,
                account.isMustChangePassword(), sessionVersion,
                Set.of(RoleCode.STUDENT), 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
        return new Dependencies(new AccountStateFilter(accounts), chain);
    }

    private static MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static UserAccountEntity account() {
        return new UserAccountEntity(
                "Sinh viên Một", "23110001", "encoded-password", null);
    }

    private record Dependencies(AccountStateFilter filter, FilterChain chain) {
    }
}

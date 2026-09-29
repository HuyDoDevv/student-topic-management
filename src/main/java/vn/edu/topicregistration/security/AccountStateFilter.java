package vn.edu.topicregistration.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;

public class AccountStateFilter extends OncePerRequestFilter {
    private static final String CHANGE_PASSWORD_PATH = "/account/password";

    private final UserAccountRepository accounts;

    public AccountStateFilter(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = applicationPath(request);
        return path.equals("/login")
                || path.equals("/logout")
                || path.equals("/error")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/vendor/")
                || path.startsWith("/img/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserAccountEntity account = accounts.findByIdAndUsername(
                        principal.accountId(), principal.getUsername())
                .orElse(null);
        if (account == null || !account.isEnabled()
                || account.getSecurityVersion() != principal.securityVersion()) {
            expireSession(request);
            response.sendRedirect(request.getContextPath() + "/login?sessionExpired");
            return;
        }

        if (account.isMustChangePassword()
                && !applicationPath(request).equals(CHANGE_PASSWORD_PATH)) {
            response.sendRedirect(request.getContextPath() + CHANGE_PASSWORD_PATH);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
    }

    private static void expireSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}

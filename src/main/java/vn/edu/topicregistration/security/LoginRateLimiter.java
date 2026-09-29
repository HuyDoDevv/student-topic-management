package vn.edu.topicregistration.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginRateLimiter extends OncePerRequestFilter
        implements AuthenticationFailureHandler, AuthenticationSuccessHandler {
    static final int USERNAME_FAILURE_LIMIT = 5;
    static final int IP_FAILURE_LIMIT = 50;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);
    static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final long CLEANUP_INTERVAL = 256;
    private static final String LOGIN_PATH = "/login";

    private final Clock clock;
    private final ConcurrentMap<String, AttemptState> usernameAttempts =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttemptState> ipAttempts =
            new ConcurrentHashMap<>();
    private final AtomicLong recordedFailures = new AtomicLong();

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !LOGIN_PATH.equals(applicationPath(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Instant now = clock.instant();
        if (isBlocked(usernameAttempts, usernameKey(request), now)
                || isBlocked(ipAttempts, ipKey(request), now)) {
            redirect(request, response, "rateLimited");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        Instant now = clock.instant();
        boolean usernameBlocked = recordFailure(
                usernameAttempts, usernameKey(request), USERNAME_FAILURE_LIMIT, now);
        boolean ipBlocked = recordFailure(
                ipAttempts, ipKey(request), IP_FAILURE_LIMIT, now);
        cleanupExpiredEntries(now);

        if (usernameBlocked || ipBlocked) {
            redirect(request, response, "rateLimited");
        } else if (exception instanceof DisabledException) {
            redirect(request, response, "disabled");
        } else {
            redirect(request, response, "error");
        }
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        usernameAttempts.remove(usernameKey(request));
        response.sendRedirect(request.getContextPath() + "/");
    }

    private boolean recordFailure(
            ConcurrentMap<String, AttemptState> attempts,
            String key,
            int limit,
            Instant now) {
        ensureCapacity(attempts, now);
        AtomicBoolean blocked = new AtomicBoolean();
        attempts.compute(key, (ignored, current) -> {
            AttemptState next;
            if (current == null || current.expiredAt(now)) {
                next = AttemptState.first(now);
            } else if (current.blockedAt(now)) {
                next = current;
            } else {
                int failures = current.failures() + 1;
                next = failures >= limit
                        ? new AttemptState(failures, current.windowStartedAt(),
                                now.plus(BLOCK_DURATION))
                        : new AttemptState(failures, current.windowStartedAt(), null);
            }
            blocked.set(next.blockedAt(now));
            return next;
        });
        recordedFailures.incrementAndGet();
        return blocked.get();
    }

    private static boolean isBlocked(
            ConcurrentMap<String, AttemptState> attempts,
            String key,
            Instant now) {
        AttemptState current = attempts.get(key);
        if (current == null) {
            return false;
        }
        if (current.blockedAt(now)) {
            return true;
        }
        if (current.expiredAt(now)) {
            attempts.remove(key, current);
        }
        return false;
    }

    private void cleanupExpiredEntries(Instant now) {
        if (recordedFailures.get() % CLEANUP_INTERVAL != 0) {
            return;
        }
        usernameAttempts.entrySet().removeIf(entry -> entry.getValue().expiredAt(now));
        ipAttempts.entrySet().removeIf(entry -> entry.getValue().expiredAt(now));
    }

    private static void ensureCapacity(
            ConcurrentMap<String, AttemptState> attempts,
            Instant now) {
        if (attempts.size() < MAX_TRACKED_KEYS) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().expiredAt(now));
        if (attempts.size() >= MAX_TRACKED_KEYS) {
            attempts.keySet().stream().findFirst().ifPresent(attempts::remove);
        }
    }

    private static String usernameKey(HttpServletRequest request) {
        String username = request.getParameter("username");
        if (username == null || username.isBlank()) {
            return "<empty>";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private static String ipKey(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank()
                ? "<unknown>"
                : remoteAddress;
    }

    private static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
    }

    private static void redirect(
            HttpServletRequest request,
            HttpServletResponse response,
            String reason) throws IOException {
        response.sendRedirect(request.getContextPath() + LOGIN_PATH + "?" + reason);
    }

    private record AttemptState(
            int failures,
            Instant windowStartedAt,
            Instant blockedUntil) {
        static AttemptState first(Instant now) {
            return new AttemptState(1, now, null);
        }

        boolean blockedAt(Instant now) {
            return blockedUntil != null && now.isBefore(blockedUntil);
        }

        boolean expiredAt(Instant now) {
            if (blockedAt(now)) {
                return false;
            }
            Instant expiresAt = blockedUntil == null
                    ? windowStartedAt.plus(ATTEMPT_WINDOW)
                    : blockedUntil;
            return !now.isBefore(expiresAt);
        }
    }
}

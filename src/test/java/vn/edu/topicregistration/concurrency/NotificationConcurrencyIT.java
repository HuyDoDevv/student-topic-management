package vn.edu.topicregistration.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.integration.FixtureSupport;
import vn.edu.topicregistration.integration.FixtureSupport.IdentityFixture;
import vn.edu.topicregistration.integration.FixtureSupport.OrganizationScope;
import vn.edu.topicregistration.integration.MySqlTestSupport;
import vn.edu.topicregistration.service.AnnouncementService;
import vn.edu.topicregistration.service.AnnouncementService.DraftCommand;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;
import vn.edu.topicregistration.service.NotificationService.EmissionResult;

/** T010: MySQL transaction, uniqueness and ownership hardening for notifications. */
@SpringBootTest
@ActiveProfiles("local")
@Import(NotificationConcurrencyIT.TestBeans.class)
class NotificationConcurrencyIT {
    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    AnnouncementService announcements;

    @Autowired
    NotificationService notifications;

    @Autowired
    NotificationTransactionProbe transactionProbe;

    @Autowired
    JdbcTemplate jdbc;

    private IdentityFixture admin;
    private IdentityFixture recipient;
    private IdentityFixture otherRecipient;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeEach
    void resetAndSeed() {
        DATABASE.resetAndMigrate();
        FixtureSupport fixtures = new FixtureSupport(
                DATABASE.dataSource(), Clock.fixed(NOW, ZoneOffset.UTC));
        OrganizationScope scope = fixtures.existingOrganization("Khoa Công nghệ Thông tin");
        admin = fixtures.createIdentity(
                "notification.concurrent.admin", "Quản trị", "NOT_A_LOGIN_HASH", true,
                null, Set.of(RoleCode.ADMIN));
        recipient = fixtures.createIdentity(
                "notification.concurrent.owner", "Người nhận", "NOT_A_LOGIN_HASH", true,
                scope, Set.of(RoleCode.STUDENT));
        otherRecipient = fixtures.createIdentity(
                "notification.concurrent.other", "Người nhận khác", "NOT_A_LOGIN_HASH", true,
                scope, Set.of(RoleCode.STUDENT));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void concurrentDuplicateEmissionCreatesOneEventAndOneDeliveryPerRecipient() throws Exception {
        EmissionCommand command = academicEvent(7_001L, List.of(
                recipient.accountId(), otherRecipient.accountId()));

        List<Outcome> outcomes = race(
                call(null, () -> transactionProbe.emit(command)),
                call(null, () -> transactionProbe.emit(command)));

        long createdResults = outcomes.stream()
                .filter(Outcome::success)
                .map(Outcome::value)
                .map(EmissionResult.class::cast)
                .filter(EmissionResult::created)
                .count();
        assertEquals(1, createdResults);
        assertEquals(1, count("""
                SELECT COUNT(*) FROM notification_events
                WHERE event_key = 'GROUP_INVITATION:7001:ACCEPTED'
                """));
        assertEquals(2, count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.event_key = 'GROUP_INVITATION:7001:ACCEPTED'
                """));
    }

    @Test
    void concurrentPublishHasOneWinnerAndDoesNotDuplicateFanOut() throws Exception {
        authenticate(admin);
        var draft = announcements.create(new DraftCommand(
                "Thông báo đồng thời", "Nội dung chỉ được công bố một lần"));
        SecurityContextHolder.clearContext();

        List<Outcome> outcomes = race(
                call(admin, () -> announcements.publish(draft.id(), draft.version())),
                call(admin, () -> announcements.publish(draft.id(), draft.version())));

        assertEquals(1, outcomes.stream().filter(Outcome::success).count());
        Outcome failure = outcomes.stream().filter(outcome -> !outcome.success())
                .findFirst()
                .orElseThrow();
        ApplicationException conflict = assertInstanceOf(
                ApplicationException.class, failure.failure());
        assertEquals(ApplicationErrorCode.CONFLICT, conflict.code());
        assertEquals(1, count(
                "SELECT COUNT(*) FROM notification_events WHERE announcement_id = ?",
                draft.id()));
        assertEquals(2, count("""
                SELECT COUNT(*)
                FROM user_notifications delivery
                JOIN notification_events event ON event.id = delivery.event_id
                WHERE event.announcement_id = ?
                """, draft.id()));
    }

    @Test
    void failedSourceTransactionRollsBackEventAndDeliveries() {
        EmissionCommand command = academicEvent(7_002L, List.of(recipient.accountId()));

        assertThrows(IllegalStateException.class,
                () -> transactionProbe.emitThenFail(command));

        assertEquals(0, count("""
                SELECT COUNT(*) FROM notification_events
                WHERE event_key = 'GROUP_INVITATION:7002:ACCEPTED'
                """));
        assertEquals(0, count("SELECT COUNT(*) FROM user_notifications"));
    }

    @Test
    void anotherAccountCannotReadOrOpenTheOwnersDelivery() {
        EmissionResult emitted = transactionProbe.emit(
                academicEvent(7_003L, List.of(recipient.accountId())));
        long deliveryId = requiredLong(
                "SELECT id FROM user_notifications WHERE event_id = ?", emitted.eventId());

        authenticate(otherRecipient);
        ApplicationException markDenied = assertThrows(ApplicationException.class,
                () -> notifications.markAsReadForCurrentActor(deliveryId));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, markDenied.code());
        ApplicationException openDenied = assertThrows(ApplicationException.class,
                () -> notifications.openForCurrentActor(deliveryId));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, openDenied.code());
        assertNull(jdbc.queryForObject(
                "SELECT read_at FROM user_notifications WHERE id = ?",
                Instant.class, deliveryId));

        authenticate(recipient);
        assertEquals("/notifications", notifications.openForCurrentActor(deliveryId));
        assertNotNull(jdbc.queryForObject(
                "SELECT read_at FROM user_notifications WHERE id = ?",
                Instant.class, deliveryId));
    }

    private EmissionCommand academicEvent(long sourceId, List<Long> recipientIds) {
        return new EmissionCommand(
                NotificationEventType.GROUP_INVITATION_ACCEPTED,
                sourceId,
                admin.accountId(),
                recipientIds,
                "Lời mời nhóm đã được chấp nhận",
                "Thành viên đã chấp nhận lời mời tham gia nhóm.",
                null,
                NotificationTargetType.NONE,
                null);
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    private long requiredLong(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        if (value == null) {
            throw new IllegalStateException("Expected database identifier");
        }
        return value;
    }

    private static AuthenticatedCall call(IdentityFixture identity, ThrowingSupplier operation) {
        return new AuthenticatedCall(identity, operation);
    }

    private static List<Outcome> race(AuthenticatedCall... calls) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(calls.length);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (AuthenticatedCall call : calls) {
                futures.add(executor.submit(() -> {
                    if (call.identity() != null) {
                        authenticate(call.identity());
                    }
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                        return new Outcome(true, call.operation().get(), null);
                    } catch (Throwable failure) {
                        return new Outcome(false, null, failure);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                }));
            }
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(20, TimeUnit.SECONDS));
            }
            return List.copyOf(outcomes);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static void authenticate(IdentityFixture identity) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        identity.username(), "unused", identity.roles().stream()
                                .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_" + role.name()))
                                .toList()));
    }

    private record AuthenticatedCall(
            IdentityFixture identity,
            ThrowingSupplier operation) {
    }

    private record Outcome(boolean success, Object value, Throwable failure) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }

    static class NotificationTransactionProbe {
        private final NotificationService notifications;

        NotificationTransactionProbe(NotificationService notifications) {
            this.notifications = notifications;
        }

        @Transactional
        public EmissionResult emit(EmissionCommand command) {
            return notifications.emit(command);
        }

        @Transactional
        public void emitThenFail(EmissionCommand command) {
            notifications.emit(command);
            throw new IllegalStateException("Force source and notification rollback");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        @Primary
        Clock notificationConcurrencyClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        NotificationTransactionProbe notificationTransactionProbe(
                NotificationService notifications) {
            return new NotificationTransactionProbe(notifications);
        }
    }
}

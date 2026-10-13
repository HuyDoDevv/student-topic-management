package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import vn.edu.topicregistration.entity.AnnouncementEntity;
import vn.edu.topicregistration.entity.NotificationEventEntity;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserNotificationEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.integration.FixtureSupport.IdentityFixture;
import vn.edu.topicregistration.integration.FixtureSupport.OrganizationScope;
import vn.edu.topicregistration.repository.AnnouncementRepository;
import vn.edu.topicregistration.repository.NotificationEventRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserNotificationRepository;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.InboxCategory;
import vn.edu.topicregistration.service.NotificationService.InboxStatus;

@SpringBootTest
@ActiveProfiles("local")
@Import(NotificationReadIT.FixedClockConfiguration.class)
class NotificationReadIT {
    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    NotificationService notifications;

    @Autowired
    NotificationEventRepository events;

    @Autowired
    AnnouncementRepository announcements;

    @Autowired
    UserNotificationRepository deliveries;

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    WebApplicationContext context;

    private IdentityFixture recipient;
    private IdentityFixture otherRecipient;
    private MockMvc mvc;

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
        recipient = fixtures.createIdentity(
                "notification.reader", "Người nhận", "NOT_A_LOGIN_HASH", true, scope,
                Set.of(RoleCode.STUDENT));
        otherRecipient = fixtures.createIdentity(
                "notification.other", "Người nhận khác", "NOT_A_LOGIN_HASH", true, scope,
                Set.of(RoleCode.STUDENT));
        authenticate(recipient);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readStateIsOwnedIdempotentAndOpenUsesTrustedTarget() {
        UserNotificationEntity own = delivery(
                recipient.accountId(), "TOPIC:77:APPROVED", NotificationEventType.TOPIC_PROPOSAL_APPROVED,
                NotificationTargetType.TOPIC, 77L, NOW.minusSeconds(20));
        UserNotificationEntity foreign = delivery(
                otherRecipient.accountId(), "TOPIC:88:APPROVED", NotificationEventType.TOPIC_PROPOSAL_APPROVED,
                NotificationTargetType.TOPIC, 88L, NOW.minusSeconds(10));

        assertEquals("/topics/77", notifications.openForCurrentActor(own.getId()));
        Instant firstReadAt = deliveries.findById(own.getId()).orElseThrow().getReadAt();
        assertEquals(NOW, firstReadAt);

        notifications.markAsReadForCurrentActor(own.getId());
        assertEquals(firstReadAt, deliveries.findById(own.getId()).orElseThrow().getReadAt());

        ApplicationException notFound = assertThrows(ApplicationException.class,
                () -> notifications.markAsReadForCurrentActor(foreign.getId()));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, notFound.code());
        assertNull(deliveries.findById(foreign.getId()).orElseThrow().getReadAt());
    }

    @Test
    void filtersInboxAndReadAllLeavesDeliveryAfterServerCutoffUnread() {
        UserNotificationEntity academic = delivery(
                recipient.accountId(), "TOPIC:91:APPROVED", NotificationEventType.TOPIC_PROPOSAL_APPROVED,
                NotificationTargetType.TOPIC, 91L, NOW.minusSeconds(10));
        UserNotificationEntity announcement = delivery(
                recipient.accountId(), "ANNOUNCEMENT:92:PUBLISHED",
                NotificationEventType.ANNOUNCEMENT_PUBLISHED,
                NotificationTargetType.ANNOUNCEMENT, 92L, NOW.minusSeconds(5));
        UserNotificationEntity later = delivery(
                recipient.accountId(), "TOPIC:93:APPROVED", NotificationEventType.TOPIC_PROPOSAL_APPROVED,
                NotificationTargetType.TOPIC, 93L, NOW.plusSeconds(1));

        notifications.markAsReadForCurrentActor(academic.getId());
        var unreadAnnouncements = notifications.inboxForCurrentActor(
                InboxStatus.UNREAD, InboxCategory.ANNOUNCEMENT, 0);
        assertEquals(1, unreadAnnouncements.getTotalElements());
        assertEquals(announcement.getId(), unreadAnnouncements.getContent().getFirst().id());

        assertEquals(1, notifications.markAllAsReadForCurrentActor());
        assertNotNull(deliveries.findById(announcement.getId()).orElseThrow().getReadAt());
        assertFalse(deliveries.findById(academic.getId()).orElseThrow().getReadAt().isAfter(NOW));
        assertNull(deliveries.findById(later.getId()).orElseThrow().getReadAt());
    }

    @Test
    void readEndpointRequiresCsrfAndCannotReadAnotherAccountsRow() throws Exception {
        UserNotificationEntity own = delivery(
                recipient.accountId(), "TOPIC:101:APPROVED", NotificationEventType.TOPIC_PROPOSAL_APPROVED,
                NotificationTargetType.TOPIC, 101L, NOW.minusSeconds(10));
        UserNotificationEntity foreign = delivery(
                otherRecipient.accountId(), "TOPIC:102:APPROVED", NotificationEventType.TOPIC_PROPOSAL_APPROVED,
                NotificationTargetType.TOPIC, 102L, NOW.minusSeconds(5));

        mvc.perform(post("/notifications/{id}/read", own.getId())
                        .with(user(recipient.username()).roles("STUDENT")))
                .andExpect(status().isForbidden());
        assertNull(deliveries.findById(own.getId()).orElseThrow().getReadAt());

        mvc.perform(post("/notifications/{id}/read", foreign.getId())
                        .with(user(recipient.username()).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        assertNull(deliveries.findById(foreign.getId()).orElseThrow().getReadAt());

        mvc.perform(post("/notifications/{id}/read", own.getId())
                        .with(user(recipient.username()).roles("STUDENT"))
                        .with(csrf()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/notifications"));
        assertNotNull(deliveries.findById(own.getId()).orElseThrow().getReadAt());
    }

    private UserNotificationEntity delivery(
            long recipientId,
            String eventKey,
            NotificationEventType eventType,
            NotificationTargetType targetType,
            Long targetId,
            Instant deliveredAt) {
        UserAccountEntity account = accounts.findById(recipientId).orElseThrow();
        AnnouncementEntity announcement = null;
        String resolvedEventKey = eventKey;
        Long resolvedTargetId = targetId;
        if (eventType == NotificationEventType.ANNOUNCEMENT_PUBLISHED) {
            announcement = announcements.saveAndFlush(new AnnouncementEntity(
                    "Thông báo kiểm thử",
                    "Nội dung kiểm thử",
                    "Nhà trường",
                    null,
                    account,
                    deliveredAt));
            resolvedEventKey = "ANNOUNCEMENT:" + announcement.getId() + ":PUBLISHED";
            resolvedTargetId = announcement.getId();
        }
        NotificationEventEntity event = events.saveAndFlush(new NotificationEventEntity(
                resolvedEventKey,
                eventType,
                "Tiêu đề " + eventKey,
                "Nội dung kiểm thử",
                null,
                announcement,
                targetType,
                resolvedTargetId,
                deliveredAt));
        return deliveries.saveAndFlush(new UserNotificationEntity(event, account, deliveredAt));
    }

    private static void authenticate(IdentityFixture identity) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        identity.username(), "unused", identity.roles().stream()
                                .map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        "ROLE_" + role.name()))
                                .toList()));
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock notificationReadClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}

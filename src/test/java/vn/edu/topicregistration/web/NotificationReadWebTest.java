package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import vn.edu.topicregistration.controller.NavigationModelAdvice;
import vn.edu.topicregistration.controller.NotificationController;
import vn.edu.topicregistration.controller.NotificationModelAdvice;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.entity.NotificationCategory;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.security.AccountPrincipal;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.HeaderView;
import vn.edu.topicregistration.service.NotificationService.InboxCategory;
import vn.edu.topicregistration.service.NotificationService.InboxStatus;
import vn.edu.topicregistration.service.NotificationService.NotificationItem;

class NotificationReadWebTest {
    private static final Instant DELIVERED_AT = Instant.parse("2026-09-24T08:15:00Z");

    private final NotificationService notifications = org.mockito.Mockito.mock(NotificationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new NotificationController(notifications))
                .setControllerAdvice(
                        new NotificationModelAdvice(notifications),
                        new NavigationModelAdvice(),
                        new WebExceptionHandler())
                .setViewResolvers(viewResolver())
                .build();
        when(notifications.headerForCurrentActor()).thenReturn(new HeaderView(1, List.of(item())));
        when(notifications.inboxForCurrentActor(InboxStatus.ALL, InboxCategory.ALL, 0))
                .thenReturn(new PageImpl<>(List.of(item()), PageRequest.of(0, 20), 1));
    }

    @Test
    void rendersFiltersAndPostActionsInsteadOfGetLinksForNotificationItems() throws Exception {
        mvc.perform(get("/notifications").principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lọc thông báo")))
                .andExpect(content().string(containsString("action=\"/notifications/41/open\"")))
                .andExpect(content().string(containsString("action=\"/notifications/41/read\"")))
                .andExpect(content().string(containsString("action=\"/notifications/read-all\"")))
                .andExpect(content().string(containsString("notification-unread")));
    }

    @Test
    void appliesUnreadAndAnnouncementFilters() throws Exception {
        when(notifications.inboxForCurrentActor(InboxStatus.UNREAD, InboxCategory.ANNOUNCEMENT, 2))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 20), 41));

        mvc.perform(get("/notifications")
                        .param("status", "UNREAD")
                        .param("category", "ANNOUNCEMENT")
                        .param("page", "2")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedStatus", InboxStatus.UNREAD))
                .andExpect(model().attribute("selectedCategory", InboxCategory.ANNOUNCEMENT));

        verify(notifications).inboxForCurrentActor(
                InboxStatus.UNREAD, InboxCategory.ANNOUNCEMENT, 2);
    }

    @Test
    void opensNotificationUsingServerResolvedTarget() throws Exception {
        when(notifications.openForCurrentActor(41L)).thenReturn("/announcements/501");

        mvc.perform(post("/notifications/41/open").principal(authentication()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/announcements/501"));

        verify(notifications).openForCurrentActor(41L);
    }

    @Test
    void marksOneAndAllAsReadWithFeedback() throws Exception {
        when(notifications.markAllAsReadForCurrentActor()).thenReturn(3);

        mvc.perform(post("/notifications/41/read").principal(authentication()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/notifications"))
                .andExpect(flash().attribute("successMessage", "Đã đánh dấu thông báo là đã đọc."));
        mvc.perform(post("/notifications/read-all").principal(authentication()))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/notifications"))
                .andExpect(flash().attribute("successMessage", "Đã đánh dấu 3 thông báo là đã đọc."));

        verify(notifications).markAsReadForCurrentActor(41L);
        verify(notifications).markAllAsReadForCurrentActor();
    }

    @Test
    void rejectsUnknownClosedFilter() throws Exception {
        mvc.perform(get("/notifications")
                        .param("status", "READ")
                        .principal(authentication()))
                .andExpect(status().isBadRequest());
    }

    private static NotificationItem item() {
        return new NotificationItem(
                41L,
                "Thông báo đã công bố",
                "Nội dung dành cho người nhận.",
                NotificationCategory.ANNOUNCEMENT,
                NotificationEventType.ANNOUNCEMENT_PUBLISHED,
                NotificationTargetType.ANNOUNCEMENT,
                501L,
                DELIVERED_AT,
                null);
    }

    private static UsernamePasswordAuthenticationToken authentication() {
        AccountPrincipal principal = new AccountPrincipal(
                7L,
                "student7",
                "unused",
                "Sinh viên 7",
                true,
                Set.of(RoleCode.STUDENT),
                1L);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, principal.getPassword(), principal.getAuthorities());
    }

    private static ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver);

        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding("UTF-8");
        return viewResolver;
    }
}

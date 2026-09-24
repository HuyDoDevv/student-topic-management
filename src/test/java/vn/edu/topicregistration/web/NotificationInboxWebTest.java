package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
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

import vn.edu.topicregistration.controller.NotificationController;
import vn.edu.topicregistration.controller.NotificationModelAdvice;
import vn.edu.topicregistration.controller.NavigationModelAdvice;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.entity.NotificationCategory;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.security.AccountPrincipal;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.HeaderView;
import vn.edu.topicregistration.service.NotificationService.NotificationItem;

class NotificationInboxWebTest {
    private static final Instant DELIVERED_AT = Instant.parse("2026-09-24T08:15:00Z");

    MockMvc mvc;

    NotificationService notifications = org.mockito.Mockito.mock(NotificationService.class);

    @BeforeEach
    void setUpDefaults() {
        mvc = standaloneSetup(new NotificationController(notifications))
                .setControllerAdvice(
                        new NotificationModelAdvice(notifications),
                        new NavigationModelAdvice(),
                        new WebExceptionHandler())
                .setViewResolvers(viewResolver())
                .build();
        when(notifications.headerForCurrentActor()).thenReturn(new HeaderView(0, List.of()));
        when(notifications.inboxForCurrentActor(0)).thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void rendersOnlyCurrentActorsInboxWithPagination() throws Exception {
        NotificationItem ownItem = item(41L, "Đề tài đã được duyệt", null);
        when(notifications.inboxForCurrentActor(1)).thenReturn(new PageImpl<>(
                List.of(ownItem), PageRequest.of(1, NotificationService.INBOX_PAGE_SIZE), 21));

        mvc.perform(get("/notifications?page=1").principal(authentication(7L)))
                .andExpect(status().isOk())
                .andExpect(view().name("notifications/list"))
                .andExpect(model().attribute("activeNav", "notifications"))
                .andExpect(model().attribute("notifications", org.hamcrest.Matchers.hasItem(ownItem)))
                .andExpect(content().string(containsString("Đề tài đã được duyệt")))
                .andExpect(content().string(containsString("Trang 2 / 2")))
                .andExpect(content().string(not(containsString("recipientId"))));

        verify(notifications).inboxForCurrentActor(1);
    }

    @Test
    void rejectsNegativePageWithoutQueryingInbox() throws Exception {
        mvc.perform(get("/notifications?page=-1").principal(authentication(7L)))
                .andExpect(status().isBadRequest());

        verify(notifications, never()).inboxForCurrentActor(-1);
    }

    @Test
    void headerShowsTopFiveAndCapsBadgeAtNinetyNinePlus() throws Exception {
        List<NotificationItem> topFive = List.of(
                item(1L, "Thông báo 1", null),
                item(2L, "Thông báo 2", null),
                item(3L, "Thông báo 3", null),
                item(4L, "Thông báo 4", DELIVERED_AT),
                item(5L, "Thông báo 5", DELIVERED_AT));
        when(notifications.headerForCurrentActor()).thenReturn(new HeaderView(137, topFive));

        mvc.perform(get("/notifications").principal(authentication(7L)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("99+")))
                .andExpect(content().string(containsString("137 thông báo chưa đọc")))
                .andExpect(content().string(containsString("Thông báo 1")))
                .andExpect(content().string(containsString("Thông báo 5")));

        verify(notifications).headerForCurrentActor();
    }

    @Test
    void rendersEmptyStatesForInboxAndHeader() throws Exception {
        mvc.perform(get("/notifications").principal(authentication(7L)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bạn chưa có thông báo")))
                .andExpect(content().string(containsString("Chưa có thông báo để hiển thị.")));
    }

    @Test
    void headerAdviceDoesNotQueryNotificationsForAnonymousRequest() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(status().isOk());

        verify(notifications, never()).headerForCurrentActor();
    }

    private static NotificationItem item(Long id, String title, Instant readAt) {
        return new NotificationItem(
                id,
                title,
                "Nội dung dành riêng cho tài khoản hiện tại.",
                NotificationCategory.ACADEMIC_EVENT,
                NotificationEventType.TOPIC_PROPOSAL_APPROVED,
                NotificationTargetType.TOPIC,
                101L,
                DELIVERED_AT,
                readAt);
    }

    private static UsernamePasswordAuthenticationToken authentication(long accountId) {
        AccountPrincipal principal = new AccountPrincipal(
                accountId,
                "student" + accountId,
                "unused",
                "Sinh viên " + accountId,
                true,
                Set.of(RoleCode.STUDENT),
                1L,
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

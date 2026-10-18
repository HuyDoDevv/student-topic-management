package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import vn.edu.topicregistration.controller.AnnouncementController;
import vn.edu.topicregistration.controller.NavigationModelAdvice;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.service.AnnouncementService;
import vn.edu.topicregistration.service.AnnouncementService.AnnouncementStatusFilter;
import vn.edu.topicregistration.service.AnnouncementService.AnnouncementView;
import vn.edu.topicregistration.service.AnnouncementService.DraftCommand;
import vn.edu.topicregistration.service.AnnouncementService.ManagerScopeView;
import vn.edu.topicregistration.service.AnnouncementService.PublicationResult;

class AnnouncementWebTest {
    private static final Instant NOW = Instant.parse("2026-09-24T08:00:00Z");

    AnnouncementService announcements;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        announcements = org.mockito.Mockito.mock(AnnouncementService.class);
        when(announcements.currentManagerScope()).thenReturn(
                new ManagerScopeView("Nhà trường", null));
        mvc = standaloneSetup(new AnnouncementController(announcements))
                .setControllerAdvice(new NavigationModelAdvice(), new WebExceptionHandler())
                .setViewResolvers(viewResolver())
                .build();
    }

    @Test
    void managementListRendersStatusPaginationAndNewAction() throws Exception {
        AnnouncementView draft = announcement(31L, null, 2L);
        when(announcements.listForCurrentManager(AnnouncementStatusFilter.DRAFT, 1))
                .thenReturn(new PageImpl<>(
                        List.of(draft), PageRequest.of(1, AnnouncementService.PAGE_SIZE), 21));

        mvc.perform(get("/announcements/manage?status=DRAFT&page=1"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcements/list"))
                .andExpect(content().string(containsString("Thông báo học vụ")))
                .andExpect(content().string(containsString("Bản nháp")))
                .andExpect(content().string(containsString("Tạo thông báo")))
                .andExpect(content().string(containsString("Trang 2 / 2")));
    }

    @Test
    void createWhitelistsContentAndRedirectsToDraftDetail() throws Exception {
        when(announcements.create(any())).thenReturn(announcement(31L, null, 0L));

        mvc.perform(post("/announcements")
                        .param("title", "Thông báo học vụ")
                        .param("body", "Nội dung")
                        .param("facultyId", "999")
                        .param("sourceName", "Nguồn giả")
                        .param("recipientIds", "1,2"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/announcements/31"));

        ArgumentCaptor<DraftCommand> command = ArgumentCaptor.forClass(DraftCommand.class);
        verify(announcements).create(command.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Thông báo học vụ", command.getValue().title());
        org.junit.jupiter.api.Assertions.assertEquals("Nội dung", command.getValue().body());
    }

    @Test
    void invalidCreateRendersFieldErrorsWithoutWriting() throws Exception {
        mvc.perform(post("/announcements")
                        .param("title", " ")
                        .param("body", ""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("announcements/form"))
                .andExpect(content().string(containsString("Chưa thể lưu thông báo")));

        verify(announcements, never()).create(any());
    }

    @Test
    void editUsesExpectedVersionAndPublishAddsFeedback() throws Exception {
        when(announcements.update(any(Long.class), any(Long.class), any()))
                .thenReturn(announcement(31L, null, 3L));
        when(announcements.publish(31L, 3L))
                .thenReturn(new PublicationResult(31L, 4L, 12, NOW));

        mvc.perform(post("/announcements/31")
                        .param("title", "Tiêu đề mới")
                        .param("body", "Nội dung mới")
                        .param("version", "2"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/announcements/31"));
        verify(announcements).update(31L, 2L, new DraftCommand("Tiêu đề mới", "Nội dung mới"));

        mvc.perform(post("/announcements/31/publish").param("version", "3"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/announcements/31"))
                .andExpect(flash().attribute("announcementResult",
                        "Đã công bố thông báo tới 12 tài khoản."));
    }

    @Test
    void detailRendersPublishedAnnouncementWithoutEditAction() throws Exception {
        when(announcements.getVisible(31L)).thenReturn(announcement(31L, NOW, 4L));

        mvc.perform(get("/announcements/31"))
                .andExpect(status().isOk())
                .andExpect(view().name("announcements/detail"))
                .andExpect(content().string(containsString("Đã công bố")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("Sửa bản nháp"))));
    }

    private static AnnouncementView announcement(Long id, Instant publishedAt, long version) {
        return new AnnouncementView(
                id,
                "Thông báo học vụ",
                "Nội dung thông báo chính thức.",
                "Nhà trường",
                null,
                NOW.minusSeconds(60),
                NOW,
                publishedAt,
                version);
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

package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import vn.edu.topicregistration.controller.NavigationModelAdvice;
import vn.edu.topicregistration.controller.NotificationModelAdvice;
import vn.edu.topicregistration.controller.RegistrationController;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.security.AccountPrincipal;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.HeaderView;
import vn.edu.topicregistration.service.RegistrationDecisionService;
import vn.edu.topicregistration.service.RegistrationService;
import vn.edu.topicregistration.service.RegistrationService.ProgressState;
import vn.edu.topicregistration.service.RegistrationService.ProgressStep;
import vn.edu.topicregistration.service.RegistrationService.RegistrationDetailView;
import vn.edu.topicregistration.service.RegistrationService.RegistrationProgressView;
import vn.edu.topicregistration.util.ProjectTime;

class RegistrationProgressWebTest {
    @Test
    void studentDetailRendersNineStepsAndSafeNextAction() throws Exception {
        RegistrationService registrations = mock(RegistrationService.class);
        when(registrations.getVisible(51L)).thenReturn(new RegistrationDetailView(
                51L, 1L, "Đợt", 21L, 31L, "Đề tài", "TLCN", "Tác giả",
                "Khoa", List.of(), List.of(), RegistrationStatus.ACCEPTED,
                null, null, Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-02T00:00:00Z"), null, 0L,
                false, false, false));
        List<ProgressStep> steps = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(number -> new ProgressStep(number, "Mốc " + number,
                        number == 3 ? ProgressState.CURRENT : ProgressState.UPCOMING,
                        number == 3 ? "/reports/51" : null,
                        number == 3 ? "Xem trang báo cáo" : null))
                .toList();
        when(registrations.progressForVisible(51L))
                .thenReturn(new RegistrationProgressView(steps, false));
        NotificationService notifications = mock(NotificationService.class);
        when(notifications.headerForCurrentActor()).thenReturn(new HeaderView(0, List.of()));

        MockMvc mvc = standaloneSetup(new RegistrationController(
                registrations, mock(RegistrationDecisionService.class),
                new ProjectTime(Clock.systemUTC())))
                .setControllerAdvice(new NavigationModelAdvice(),
                        new NotificationModelAdvice(notifications))
                .setViewResolvers(viewResolver())
                .build();
        AccountPrincipal principal = new AccountPrincipal(31L, "student31", "unused",
                "Sinh viên", true, Set.of(RoleCode.STUDENT), 7L);

        mvc.perform(get("/registrations/51").principal(
                        UsernamePasswordAuthenticationToken.authenticated(
                                principal, principal.getPassword(), principal.getAuthorities())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mốc 9")))
                .andExpect(content().string(containsString("href=\"/reports/51\"")))
                .andExpect(content().string(containsString("Chưa đến")));
    }

    private static ThymeleafViewResolver viewResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
        viewResolver.setTemplateEngine(engine);
        viewResolver.setCharacterEncoding("UTF-8");
        return viewResolver;
    }
}

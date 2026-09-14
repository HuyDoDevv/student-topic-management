package vn.edu.topicregistration.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@ActiveProfiles("preview")
class FoundationWebTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void shellRendersLocalAssets() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Quản lý đề tài sinh viên")))
            .andExpect(content().string(containsString("/vendor/tabler/1.4.0/css/tabler.min.css")));
        mvc.perform(get("/vendor/tabler/1.4.0/css/tabler.min.css")).andExpect(status().isOk());
        mvc.perform(get("/vendor/tabler/1.4.0/js/tabler.min.js")).andExpect(status().isOk());
    }

    @Test void dashboardPreviewUsesRoleSpecificContentAndNoFutureResultMetric() throws Exception {
        mvc.perform(get("/dashboard?role=dean"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đề tài chờ công bố")))
            .andExpect(content().string(containsString("Đăng ký chờ xét")));
        mvc.perform(get("/dashboard?role=lecturer"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Bản nháp")))
            .andExpect(content().string(containsString("Tổng quan đề tài của tôi")));
        mvc.perform(get("/dashboard?role=student"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("ĐĂNG KÝ THÀNH CÔNG")))
            .andExpect(content().string(containsString("href=\"/registrations/1\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Kết quả công bố"))));
        mvc.perform(get("/dashboard?role=admin"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Không có dữ liệu học vụ")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Đề tài chờ duyệt"))));
    }

    @Test void previewCatalogueRendersEveryBusinessTemplate() throws Exception {
        String[] routes = {
            "/login", "/dashboard", "/periods", "/periods/new", "/periods/1",
            "/periods/1/edit", "/periods/1/topics", "/periods/1/topics/new",
            "/topics/101", "/topics/101/edit", "/periods/1/topics/review",
            "/periods/1/groups/new", "/groups/1", "/groups/1/registrations/new",
            "/registrations/1", "/registrations/1/decision"
        };

        for (String route : routes) {
            mvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test void deanPreviewSeparatesStudentRegistrationsFromLecturerProposals() throws Exception {
        mvc.perform(get("/periods/1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Xét nhóm đăng ký đề tài")))
            .andExpect(content().string(containsString("Mở danh sách đề xuất")));

        mvc.perform(get("/periods/1/topics/review"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Danh sách đề xuất của giảng viên")))
            .andExpect(content().string(containsString("Sinh viên chưa thể đăng ký")));
    }

    @Test void registrationPreviewExplainsCompleteGroupAndSubmissionSnapshot() throws Exception {
        mvc.perform(get("/groups/1/registrations/new"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("đúng 3 thành viên")));

        mvc.perform(get("/registrations/1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Thành viên tại thời điểm gửi")));
    }

    @Test void groupMemberSeesAcceptedRegistrationFeedback() throws Exception {
        mvc.perform(get("/groups/1"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đăng ký đề tài đã được chấp nhận")))
            .andExpect(content().string(containsString("Hệ thống quản lý đăng ký đề tài")))
            .andExpect(content().string(containsString("href=\"/registrations/1\"")));

        mvc.perform(get("/groups/2"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đăng ký đề tài đang chờ xét")))
            .andExpect(content().string(containsString("href=\"/registrations/2\"")));
    }

    @Test void previewStillRejectsUnknownOrMutatingRoutes() throws Exception {
        mvc.perform(get("/not-a-page")).andExpect(status().isNotFound());
        mvc.perform(post("/register")).andExpect(status().isForbidden());
        mvc.perform(post("/")).andExpect(status().isForbidden());
    }
}

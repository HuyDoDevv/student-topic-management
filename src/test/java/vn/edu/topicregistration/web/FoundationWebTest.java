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
            .andExpect(content().string(containsString("/vendor/tabler/1.4.0/css/tabler.min.css")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                    containsString("action=\"/logout\""))));
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
            .andExpect(content().string(containsString("Tổng tài khoản")))
            .andExpect(content().string(containsString("Sinh viên")))
            .andExpect(content().string(containsString("Giảng viên")))
            .andExpect(content().string(containsString("Trưởng khoa")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Đề tài chờ duyệt"))));
    }

    @Test void previewCatalogueRendersEveryBusinessTemplate() throws Exception {
        String[] routes = {
            "/login", "/dashboard", "/periods", "/periods/new", "/periods/1",
            "/periods/1/edit", "/periods/1/topics", "/periods/1/topics/new",
            "/topics/101", "/topics/101/edit", "/periods/1/topics/review",
            "/periods/1/groups/new", "/groups/1", "/groups/1/registrations/new",
            "/registrations/1", "/registrations/1/decision",
            "/admin/accounts/students", "/admin/accounts/lecturers",
            "/admin/accounts/deans", "/admin/accounts/new"
        };

        for (String route : routes) {
            mvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test void adminAccountPreviewSeparatesDashboardRoleListsAndProvisioning() throws Exception {
        mvc.perform(get("/admin/accounts/students"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Quản lý sinh viên")))
            .andExpect(content().string(containsString("Tìm sinh viên")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Import tài khoản"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Tổng tài khoản"))));

        mvc.perform(get("/admin/accounts/lecturers"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Quản lý giảng viên")));

        mvc.perform(get("/admin/accounts/deans"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Quản lý trưởng khoa")));

        mvc.perform(get("/admin/accounts/new"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Tạo tài khoản")))
            .andExpect(content().string(containsString("Import tài khoản bằng CSV")))
            .andExpect(content().string(containsString("Tải file mẫu")));

        mvc.perform(get("/admin/accounts"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/accounts/students"));
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

    @Test void deanPeriodListShowsActiveWorkflowStatisticsAndFilter() throws Exception {
        mvc.perform(get("/periods"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Lọc trạng thái đợt")))
            .andExpect(content().string(containsString("Đề tài chờ duyệt")))
            .andExpect(content().string(containsString("Nhóm chờ xét")))
            .andExpect(content().string(containsString("Đề tài chờ công bố")))
            .andExpect(content().string(containsString("Đang nhận đề tài GV")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                    containsString("Đợt đã kết thúc"))));
    }

    @Test void lecturerPeriodsAreSplitAndExpiredPeriodDoesNotOfferCreateAction() throws Exception {
        mvc.perform(get("/topics"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đợt còn hiệu lực")))
            .andExpect(content().string(containsString("Đợt đã hết hạn")));

        mvc.perform(get("/periods/3/topics"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Đã ngoài thời gian tạo đề tài mới")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                    containsString("href=\"/periods/3/topics/new\""))));
    }

    @Test void studentPeriodsAreSeparatedByRegistrationPhase() throws Exception {
        mvc.perform(get("/groups"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Sắp đăng ký")))
            .andExpect(content().string(containsString("Đang đăng ký")))
            .andExpect(content().string(containsString("Đã kết thúc")))
            .andExpect(content().string(containsString("href=\"/periods/4/groups/new\"")))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                    containsString("href=\"/periods/2/groups/new\""))))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                    containsString("href=\"/periods/3/groups/new\""))));
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

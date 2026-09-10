package vn.edu.topicregistration.shared.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import vn.edu.topicregistration.periods.domain.PeriodRules.TopicType;
import vn.edu.topicregistration.periods.web.PeriodForm;
import vn.edu.topicregistration.shared.time.TimeConfig;

/**
 * Supplies deterministic sample view models for reviewing Thymeleaf pages.
 * This controller is unavailable when the application uses the local database profile.
 */
@Controller
@Profile("preview")
public class UiPreviewController {

    private static final List<PreviewPeriod> PERIODS = List.of(
            new PreviewPeriod(
                    1, "Đồ án chuyên ngành HK1 2026–2027", "Đồ án chuyên ngành",
                    "01/09/2026", "15/09/2026",
                    "20/09/2026", "30/09/2026",
                    null, null, "Đang nhận đề tài", "bg-green-lt"),
            new PreviewPeriod(
                    2, "Khóa luận tốt nghiệp HK1 2026–2027", "Khóa luận tốt nghiệp",
                    "15/08/2026", "30/08/2026",
                    "05/09/2026", "12/09/2026",
                    "20/12/2026", "10/01/2027", "Đã đóng", "bg-secondary-lt"));

    private static final List<Map<String, Object>> TOPICS = List.of(
            Map.of("id", 101, "title", "Hệ thống quản lý đăng ký đề tài", "author", "TS. Nguyễn Văn An",
                    "department", "Công nghệ phần mềm", "supervisors", "TS. Nguyễn Văn An",
                    "status", "PUBLISHED"),
            Map.of("id", 102, "title", "Phân tích dữ liệu học tập bằng học máy", "author", "ThS. Trần Thu Hà",
                    "department", "Hệ thống thông tin", "supervisors", "ThS. Trần Thu Hà, TS. Lê Minh",
                    "status", "PENDING_APPROVAL"),
            Map.of("id", 103, "title", "Ứng dụng di động hỗ trợ sinh viên", "author", "ThS. Phạm Quốc Bảo",
                    "department", "Công nghệ phần mềm", "supervisors", "ThS. Phạm Quốc Bảo",
                    "status", "DRAFT"));

    private static final List<Map<String, Object>> MEMBERS = List.of(
            Map.of("id", 201, "code", "23110001", "name", "Nguyễn Minh Anh", "role", "Trưởng nhóm"),
            Map.of("id", 202, "code", "23110018", "name", "Trần Gia Huy", "role", "Thành viên"),
            Map.of("id", 203, "code", "23110102", "name", "Lê Hoàng Nam", "role", "Thành viên"));

    @GetMapping("/login")
    String login() {
        return "auth/login";
    }

    @GetMapping("/dashboard")
    String dashboard(Model model) {
        common(model, "dashboard", "Không gian làm việc");
        model.addAttribute("today", LocalDate.now(TimeConfig.BUSINESS_ZONE).format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))));
        model.addAttribute("topicCount", 12);
        model.addAttribute("groupCount", 7);
        model.addAttribute("periodCount", 2);
        model.addAttribute("publishedResultCount", 4);
        model.addAttribute("currentPeriod", PERIODS.getFirst());
        return "dashboard/index";
    }

    @GetMapping("/periods")
    String periodList(Model model) {
        common(model, "periods", "Quản lý học vụ");
        model.addAttribute("periods", PERIODS);
        return "periods/list";
    }

    @GetMapping("/periods/new")
    String periodCreate(Model model) {
        common(model, "periods", "Quản lý học vụ");
        model.addAttribute("editing", false);
        model.addAttribute("form", new PeriodForm());
        model.addAttribute("locks", previewLocks(false));
        model.addAttribute("today", LocalDate.now(TimeConfig.BUSINESS_ZONE));
        return "periods/form";
    }

    @GetMapping("/periods/{id}/edit")
    String periodEdit(@PathVariable long id, Model model) {
        common(model, "periods", "Quản lý học vụ");
        model.addAttribute("editing", true);
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("form", previewPeriodForm());
        model.addAttribute("locks", previewLocks(false));
        model.addAttribute("today", LocalDate.now(TimeConfig.BUSINESS_ZONE));
        return "periods/form";
    }

    @GetMapping("/periods/{id}")
    String periodDetail(@PathVariable long id, Model model) {
        common(model, "periods", "Quản lý học vụ");
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("topics", TOPICS);
        return "periods/detail";
    }

    @GetMapping("/periods/{periodId}/topics")
    String topicList(@PathVariable long periodId, Model model) {
        common(model, "topics", "Đề tài");
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("topics", TOPICS);
        return "topics/list";
    }

    @GetMapping("/periods/{periodId}/topics/new")
    String topicCreate(@PathVariable long periodId, Model model) {
        common(model, "topics", "Đề tài");
        model.addAttribute("editing", false);
        model.addAttribute("period", PERIODS.getFirst());
        return "topics/form";
    }

    @GetMapping("/topics/{id}/edit")
    String topicEdit(@PathVariable long id, Model model) {
        common(model, "topics", "Đề tài");
        model.addAttribute("editing", true);
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("topic", TOPICS.getFirst());
        return "topics/form";
    }

    @GetMapping("/topics/{id}")
    String topicDetail(@PathVariable long id, Model model) {
        common(model, "topics", "Đề tài");
        model.addAttribute("topic", TOPICS.getFirst());
        model.addAttribute("period", PERIODS.getFirst());
        return "topics/detail";
    }

    @GetMapping("/periods/{periodId}/topics/review")
    String topicReview(@PathVariable long periodId, Model model) {
        common(model, "reviews", "Duyệt đề tài");
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("topics", TOPICS.subList(1, 2));
        return "topics/review";
    }

    @GetMapping("/periods/{periodId}/groups/new")
    String groupCreate(@PathVariable long periodId, Model model) {
        common(model, "groups", "Nhóm sinh viên");
        model.addAttribute("period", PERIODS.getFirst());
        return "groups/form";
    }

    @GetMapping("/groups/{id}")
    String groupDetail(@PathVariable long id, Model model) {
        common(model, "groups", "Nhóm sinh viên");
        model.addAttribute("groupCode", "N01");
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("members", MEMBERS);
        return "groups/detail";
    }

    @GetMapping("/groups/{groupId}/registrations/new")
    String registrationCreate(@PathVariable long groupId, Model model) {
        common(model, "registrations", "Đăng ký đề tài");
        model.addAttribute("groupCode", "N01");
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("topics", TOPICS.subList(0, 1));
        return "registrations/form";
    }

    @GetMapping("/registrations/{id}")
    String registrationDetail(@PathVariable long id, Model model) {
        common(model, "registrations", "Đăng ký đề tài");
        model.addAttribute("registrationId", id);
        model.addAttribute("registrationStatus", "PENDING");
        model.addAttribute("groupCode", "N01");
        model.addAttribute("topic", TOPICS.getFirst());
        model.addAttribute("members", MEMBERS);
        return "registrations/detail";
    }

    @GetMapping("/registrations/{id}/decision")
    String registrationDecision(@PathVariable long id, Model model) {
        common(model, "decisions", "Xét duyệt đăng ký");
        model.addAttribute("registrationId", id);
        model.addAttribute("groupCode", "N01");
        model.addAttribute("topic", TOPICS.getFirst());
        model.addAttribute("members", MEMBERS);
        return "registrations/decision";
    }

    private void common(Model model, String activeNav, String pageSection) {
        model.addAttribute("activeNav", activeNav);
        model.addAttribute("pageSection", pageSection);
        model.addAttribute("currentUserName", "Nguyễn Văn An");
        model.addAttribute("currentRoleLabel", "Chế độ xem trước");
        model.addAttribute("currentUserInitials", "NA");
    }

    private PeriodForm previewPeriodForm() {
        PeriodForm form = new PeriodForm();
        form.setName("Đồ án chuyên ngành HK1 2026–2027");
        form.setTopicType(TopicType.COURSE);
        form.setLecturerStart(LocalDate.of(2026, 9, 1));
        form.setLecturerEnd(LocalDate.of(2026, 9, 15));
        form.setStudentStart(LocalDate.of(2026, 9, 20));
        form.setStudentEnd(LocalDate.of(2026, 9, 30));
        form.setVersion(0L);
        return form;
    }

    private Map<String, Boolean> previewLocks(boolean locked) {
        return Map.of(
                "base", locked,
                "lecturerEnd", locked,
                "studentEnd", locked,
                "reviewerScoreDeadline", locked,
                "councilReportDate", locked);
    }

    public record PreviewPeriod(
            int id,
            String name,
            String type,
            String lecturerStart,
            String lecturerEnd,
            String studentStart,
            String studentEnd,
            String reviewerScoreDeadline,
            String councilReportDate,
            String status,
            String statusClass) {
        public String lecturerWindow() {
            return lecturerStart + " – " + lecturerEnd;
        }

        public String studentWindow() {
            return studentStart + " – " + studentEnd;
        }
    }
}

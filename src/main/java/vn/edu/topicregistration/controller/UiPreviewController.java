package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.dto.GroupMemberForm;
import vn.edu.topicregistration.dto.PeriodForm;
import vn.edu.topicregistration.entity.RegistrationStatus;
import vn.edu.topicregistration.dto.RegistrationDecisionForm;
import vn.edu.topicregistration.dto.RegistrationForm;
import vn.edu.topicregistration.dto.DashboardView;
import vn.edu.topicregistration.config.TimeConfig;
import vn.edu.topicregistration.dto.TopicDraftForm;

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
import org.springframework.web.bind.annotation.RequestParam;

import vn.edu.topicregistration.service.PeriodRules.TopicType;
import vn.edu.topicregistration.service.GroupService.StudentPeriodPhase;
import vn.edu.topicregistration.dto.DashboardView.Focus;
import vn.edu.topicregistration.dto.DashboardView.Metric;

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
                    null, null, "Đang nhận đề tài GV", "bg-green-lt", 1, 2, 1,
                    true, true),
            new PreviewPeriod(
                    2, "Khóa luận tốt nghiệp HK1 2026–2027", "Khóa luận tốt nghiệp",
                    "01/10/2026", "10/10/2026",
                    "15/10/2026", "25/10/2026",
                    "20/12/2026", "10/01/2027",
                    "Sắp nhận đề tài GV", "bg-azure-lt", 0, 0, 0,
                    false, true),
            new PreviewPeriod(
                    3, "Đồ án chuyên ngành HK2 2025–2026", "Đồ án chuyên ngành",
                    "01/03/2026", "15/03/2026",
                    "20/03/2026", "31/03/2026",
                    null, null, "Đã kết thúc", "bg-secondary-lt", 0, 0, 0,
                    false, false));

    private static final List<PreviewTopic> TOPICS = List.of(
            new PreviewTopic(
                    101, "Hệ thống quản lý đăng ký đề tài", "TS. Nguyễn Văn An",
                    "Công nghệ phần mềm", "TS. Nguyễn Văn An", "Đã công bố", "bg-green-lt",
                    "Thiết kế hệ thống tập trung để quản lý đợt, đề tài, nhóm sinh viên và quá trình đăng ký.",
                    "Xây dựng luồng đăng ký có kiểm soát quyền, thời gian và trạng thái.",
                    "Nắm vững Java, Spring Boot và cơ sở dữ liệu quan hệ.",
                    "Ứng dụng web chạy được, mã nguồn và tài liệu thiết kế.", 2, false),
            new PreviewTopic(
                    102, "Phân tích dữ liệu học tập bằng học máy", "ThS. Trần Thu Hà",
                    "Hệ thống thông tin", "ThS. Trần Thu Hà, TS. Lê Minh", "Chờ duyệt", "bg-yellow-lt",
                    "Phân tích dữ liệu kết quả học tập để hỗ trợ cố vấn học tập.",
                    "Xây dựng mô hình dự báo và trang tổng hợp kết quả.",
                    "Có kiến thức Python, thống kê và học máy.",
                    "Mô hình thử nghiệm, báo cáo đánh giá và ứng dụng minh họa.", 1, false),
            new PreviewTopic(
                    103, "Ứng dụng di động hỗ trợ sinh viên", "ThS. Phạm Quốc Bảo",
                    "Công nghệ phần mềm", "ThS. Phạm Quốc Bảo", "Nháp", "bg-secondary-lt",
                    "Ứng dụng tập hợp thông báo và các tiện ích dành cho sinh viên.",
                    "Hoàn thiện bản thử nghiệm trên thiết bị di động.",
                    "Biết lập trình ứng dụng di động và làm việc với REST API.",
                    "Ứng dụng thử nghiệm và tài liệu hướng dẫn sử dụng.", 0, true));

    private static final List<PreviewLecturer> LECTURERS = List.of(
            new PreviewLecturer(11, "TS. Nguyễn Văn An", "nguyenvanan"),
            new PreviewLecturer(12, "ThS. Phạm Quốc Bảo", "phamquocbao"),
            new PreviewLecturer(13, "ThS. Trần Thu Hà", "tranthuha"));

    private static final List<Map<String, Object>> MEMBERS = List.of(
            Map.of("id", 201, "code", "23110001", "name", "Nguyễn Minh Anh", "role", "Trưởng nhóm"),
            Map.of("id", 202, "code", "23110018", "name", "Trần Gia Huy", "role", "Thành viên"),
            Map.of("id", 203, "code", "23110102", "name", "Lê Hoàng Nam", "role", "Thành viên"));

    private static final PreviewGroupPeriod GROUP_PERIOD = new PreviewGroupPeriod(
            1, "Đồ án chuyên ngành HK1 2026–2027", "20/09/2026", "30/09/2026",
            StudentPeriodPhase.OPEN, true, false);
    private static final List<PreviewGroupPeriod> GROUP_PERIODS = List.of(
            new PreviewGroupPeriod(
                    2, "Khóa luận tốt nghiệp HK1 2026–2027", "15/10/2026", "25/10/2026",
                    StudentPeriodPhase.UPCOMING, false, false),
            GROUP_PERIOD,
            new PreviewGroupPeriod(
                    4, "Nghiên cứu khoa học HK1 2026–2027", "20/09/2026", "30/09/2026",
                    StudentPeriodPhase.OPEN, false, false),
            new PreviewGroupPeriod(
                    3, "Đồ án chuyên ngành HK2 2025–2026", "20/03/2026", "31/03/2026",
                    StudentPeriodPhase.ENDED, true, true));

    private static final List<PreviewGroupMember> GROUP_MEMBERS = List.of(
            new PreviewGroupMember(201, "Nguyễn Minh Anh", "23110001", 1, true),
            new PreviewGroupMember(202, "Trần Gia Huy", "23110018", 2, false),
            new PreviewGroupMember(203, "Lê Hoàng Nam", "23110102", 3, false));

    private static final PreviewGroup GROUP = new PreviewGroup(
            1, 1, 201, 0, GROUP_MEMBERS, false, false, false, false, true,
            new PreviewGroupRegistration(
                    1,
                    TOPICS.getFirst().title(),
                    "ACCEPTED",
                    "Đã chấp nhận",
                    "bg-green-lt",
                    true),
            0);

    private static final PreviewGroup PENDING_GROUP = new PreviewGroup(
            2, 1, 201, 0, GROUP_MEMBERS, false, false, false, false, true,
            new PreviewGroupRegistration(
                    2,
                    TOPICS.getFirst().title(),
                    "PENDING",
                    "Chờ xét",
                    "bg-yellow-lt",
                    false),
            0);

    private static final List<PreviewStudent> GROUP_CANDIDATES = List.of(
            new PreviewStudent(203, "Lê Hoàng Nam", "23110102"),
            new PreviewStudent(204, "Võ Khánh Linh", "23110125"));

    private static final List<PreviewInvitation> GROUP_INVITATIONS = List.of(
            new PreviewInvitation(
                    1, 1, 1, "Đồ án chuyên ngành HK1 2026–2027",
                    "Lê Hoàng Nam", "23110102", "Nguyễn Minh Anh", 3,
                    "Đang chờ phản hồi", "bg-yellow-lt", "11/09/2026 09:30", null,
                    0, true, true, true, true));

    @GetMapping("/login")
    String login() {
        return "auth/login";
    }

    @GetMapping({"/", "/dashboard"})
    String dashboard(@RequestParam(defaultValue = "student") String role, Model model) {
        common(model, "dashboard", "Không gian làm việc");
        model.addAttribute("today", LocalDate.now(TimeConfig.BUSINESS_ZONE).format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))));
        model.addAttribute("dashboard", previewDashboard(role));
        return "dashboard/index";
    }

    private DashboardView previewDashboard(String role) {
        return switch (role) {
            case "dean" -> new DashboardView("Trưởng khoa", "Tổng quan duyệt học vụ",
                    "Chỉ hiển thị hồ sơ thuộc khoa của bạn.", List.of(
                    new Metric("Đề tài chờ duyệt", 1, "Đề xuất chưa được quyết định", "/periods"),
                    new Metric("Đề tài chờ công bố", 2, "Đã duyệt", "/periods"),
                    new Metric("Đăng ký chờ xét", 3, "Nhóm chờ quyết định", "/periods"),
                    new Metric("Đợt còn hiệu lực", 2, "Chưa kết thúc đăng ký sinh viên", "/periods")),
                    new Focus("VIỆC CẦN XỬ LÝ", "Có 3 đăng ký chờ xét",
                            "Mở đợt đăng ký để xét nhóm.", "/periods", "Xem các đợt", "bg-yellow-lt"));
            case "lecturer" -> new DashboardView("Giảng viên", "Tổng quan đề tài của tôi",
                    "Theo dõi đề tài bạn đề xuất.", List.of(
                    new Metric("Bản nháp", 1, "Chưa gửi duyệt", "/topics"),
                    new Metric("Chờ duyệt", 1, "Đã gửi trưởng khoa", "/topics"),
                    new Metric("Đã duyệt", 0, "Chưa công bố", "/topics"),
                    new Metric("Đã công bố", 1, "Sinh viên có thể xem", "/topics")),
                    new Focus("VIỆC CỦA BẠN", "Có 1 đề tài nháp",
                            "Hoàn thiện và gửi duyệt.", "/topics", "Xem đề tài của tôi", "bg-blue-lt"));
            case "admin" -> new DashboardView("Quản trị sự cố", "Tổng quan quản trị",
                    "Không có quyền xem hoặc quyết định học vụ.", List.of(),
                    new Focus("PHẠM VI QUYỀN", "Không có dữ liệu học vụ",
                            "Chỉ xử lý sự cố khẩn cấp.", null, null, "bg-secondary-lt"));
            default -> new DashboardView("Sinh viên", "Tổng quan đăng ký của tôi",
                    "Theo dõi nhóm và kết quả đăng ký của chính bạn.", List.of(
                    new Metric("Lời mời cần phản hồi", 0, "Còn trong hạn", "/group-invitations"),
                    new Metric("Nhóm đang tham gia", 1, "Nhóm hiện tại", "/groups"),
                    new Metric("Đăng ký chờ xét", 0, "Đang chờ trưởng khoa", "/groups"),
                    new Metric("Đăng ký được chấp nhận", 1, "Đăng ký thành công", "/groups")),
                    new Focus("ĐĂNG KÝ THÀNH CÔNG", "Hệ thống quản lý đăng ký đề tài",
                            "Trưởng khoa đã chấp nhận đăng ký đề tài của nhóm bạn.",
                            "/registrations/1", "Xem đăng ký", "bg-green-lt"));
        };
    }

    @GetMapping("/periods")
    String periodList(Model model) {
        common(model, "periods", "Quản lý học vụ");
        model.addAttribute("periods", PERIODS.stream()
                .filter(PreviewPeriod::active)
                .toList());
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
        addReviewPreview(model);
        model.addAttribute("registrationRequests", List.of(new PreviewRegistrationRequest(
                1L, "N001", "Nguyễn Minh Anh", 3, TOPICS.getFirst().title(),
                "11/09/2026 10:30", "Chờ xét", "bg-yellow-lt", null, true)));
        model.addAttribute("pendingRegistrationCount", 1);
        return "periods/detail";
    }

    @GetMapping("/periods/{periodId}/topics")
    String topicList(@PathVariable long periodId, Model model) {
        common(model, "topics", "Đề tài");
        model.addAttribute("period", previewPeriod(periodId));
        model.addAttribute("topics", TOPICS);
        return "topics/list";
    }

    @GetMapping("/topics")
    String topicPeriods(Model model) {
        common(model, "topics", "Đề tài");
        model.addAttribute("activePeriods", PERIODS.stream()
                .filter(PreviewPeriod::active)
                .toList());
        model.addAttribute("expiredPeriods", PERIODS.stream()
                .filter(period -> !period.active())
                .toList());
        return "topics/period-selection";
    }

    @GetMapping("/periods/{periodId}/topics/new")
    String topicCreate(@PathVariable long periodId, Model model) {
        common(model, "topics", "Đề tài");
        model.addAttribute("editing", false);
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("form", new TopicDraftForm());
        model.addAttribute("departmentName", "Công nghệ phần mềm");
        model.addAttribute("primarySupervisor", LECTURERS.getFirst());
        model.addAttribute("coSupervisors", LECTURERS.subList(1, LECTURERS.size()));
        return "topics/form";
    }

    @GetMapping("/topics/{id}/edit")
    String topicEdit(@PathVariable long id, Model model) {
        common(model, "topics", "Đề tài");
        model.addAttribute("editing", true);
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("topic", TOPICS.getLast());
        model.addAttribute("form", previewTopicForm());
        model.addAttribute("departmentName", "Công nghệ phần mềm");
        model.addAttribute("primarySupervisor", LECTURERS.getFirst());
        model.addAttribute("coSupervisors", LECTURERS.subList(1, LECTURERS.size()));
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
        addReviewPreview(model);
        return "topics/review";
    }

    private void addReviewPreview(Model model) {
        model.addAttribute("reviewTopics", List.of(Map.ofEntries(
                Map.entry("id", 102),
                Map.entry("title", TOPICS.get(1).title()),
                Map.entry("author", TOPICS.get(1).author()),
                Map.entry("department", TOPICS.get(1).department()),
                Map.entry("supervisorNames", TOPICS.get(1).supervisors()),
                Map.entry("description", TOPICS.get(1).description()),
                Map.entry("objectives", TOPICS.get(1).objectives()),
                Map.entry("studentRequirements", TOPICS.get(1).studentRequirements()),
                Map.entry("expectedResults", TOPICS.get(1).expectedResults()),
                Map.entry("status", "Chờ duyệt"),
                Map.entry("statusClass", "bg-yellow-lt"),
                Map.entry("version", 1L),
                Map.entry("pending", true),
                Map.entry("approved", false),
                Map.entry("published", false))));
        model.addAttribute("reviewSummary", Map.of(
                "pendingCount", 1,
                "approvedCount", 0,
                "publishedCount", 1));
        model.addAttribute("reviewNotice", Map.of(
                "warning", false,
                "title", "Hoàn tất trước ngày 20/09/2026",
                "message", "Trưởng khoa cần duyệt và công bố đề tài trước khi sinh viên bắt đầu đăng ký."));
    }

    @GetMapping("/groups")
    String groupPeriods(Model model) {
        common(model, "groups", "Nhóm sinh viên");
        model.addAttribute("upcomingPeriods", previewGroupPeriods(StudentPeriodPhase.UPCOMING));
        model.addAttribute("openPeriods", previewGroupPeriods(StudentPeriodPhase.OPEN));
        model.addAttribute("endedPeriods", previewGroupPeriods(StudentPeriodPhase.ENDED));
        model.addAttribute("pendingInvitationCount", 1);
        return "groups/list";
    }

    @GetMapping("/periods/{periodId}/groups/new")
    String groupCreate(@PathVariable long periodId, Model model) {
        common(model, "groups", "Nhóm sinh viên");
        model.addAttribute("period", GROUP_PERIOD);
        return "groups/form";
    }

    @GetMapping("/groups/{id}")
    String groupDetail(@PathVariable long id, Model model) {
        common(model, "groups", "Nhóm sinh viên");
        PreviewGroup group = id == PENDING_GROUP.id() ? PENDING_GROUP : GROUP;
        GroupMemberForm invitationForm = new GroupMemberForm();
        invitationForm.setVersion(group.version());
        GroupMemberForm leadershipForm = new GroupMemberForm();
        leadershipForm.setVersion(group.version());
        model.addAttribute("groupCode", "N" + String.format("%03d", group.id()));
        model.addAttribute("period", GROUP_PERIOD);
        model.addAttribute("group", group);
        model.addAttribute("candidates", GROUP_CANDIDATES);
        model.addAttribute("invitations", GROUP_INVITATIONS);
        model.addAttribute("invitationForm", invitationForm);
        model.addAttribute("leadershipForm", leadershipForm);
        return "groups/detail";
    }

    @GetMapping("/group-invitations")
    String groupInvitations(Model model) {
        common(model, "group-invitations", "Nhóm sinh viên");
        model.addAttribute("invitations", GROUP_INVITATIONS);
        return "groups/invitations";
    }

    @GetMapping("/groups/{groupId}/registrations/new")
    String registrationCreate(@PathVariable long groupId, Model model) {
        common(model, "registrations", "Đăng ký đề tài");
        RegistrationForm form = new RegistrationForm();
        form.setVersion(0L);
        model.addAttribute("registrationForm", form);
        model.addAttribute("groupId", groupId);
        model.addAttribute("groupCode", "N001");
        model.addAttribute("period", PERIODS.getFirst());
        model.addAttribute("topics", TOPICS.subList(0, 1));
        model.addAttribute("memberCount", 3);
        model.addAttribute("pendingInvitation", false);
        return "registrations/form";
    }

    @GetMapping("/registrations/{id}")
    String registrationDetail(@PathVariable long id, Model model) {
        common(model, "registrations", "Đăng ký đề tài");
        RegistrationDecisionForm form = new RegistrationDecisionForm();
        form.setVersion(0L);
        model.addAttribute("withdrawForm", form);
        model.addAttribute("registration", previewRegistration(id, false));
        model.addAttribute("groupCode", "N001");
        model.addAttribute("members", MEMBERS);
        return "registrations/detail";
    }

    @GetMapping("/registrations/{id}/decision")
    String registrationDecision(@PathVariable long id, Model model) {
        common(model, "decisions", "Xét duyệt đăng ký");
        RegistrationDecisionForm acceptForm = new RegistrationDecisionForm();
        acceptForm.setVersion(0L);
        RegistrationDecisionForm rejectForm = new RegistrationDecisionForm();
        rejectForm.setVersion(0L);
        model.addAttribute("acceptForm", acceptForm);
        model.addAttribute("rejectForm", rejectForm);
        model.addAttribute("registration", previewRegistration(id, true));
        model.addAttribute("groupCode", "N001");
        model.addAttribute("members", MEMBERS);
        return "registrations/decision";
    }

    private PreviewRegistration previewRegistration(long id, boolean deanViewer) {
        return new PreviewRegistration(
                id,
                1L,
                PERIODS.getFirst().name(),
                1L,
                101L,
                TOPICS.getFirst().title(),
                TOPICS.getFirst().author(),
                TOPICS.getFirst().department(),
                TOPICS.getFirst().supervisors(),
                RegistrationStatus.PENDING,
                "Chờ xét",
                "bg-yellow-lt",
                null,
                "11/09/2026 10:30",
                null,
                null,
                0L,
                !deanViewer,
                deanViewer,
                deanViewer);
    }

    private void common(Model model, String activeNav, String pageSection) {
        model.addAttribute("activeNav", activeNav);
        model.addAttribute("pageSection", pageSection);
        model.addAttribute("currentUserName", "Nguyễn Văn An");
        model.addAttribute("currentRoleLabel", "Chế độ xem trước");
        model.addAttribute("currentUserInitials", "NA");
        model.addAttribute("canManagePeriods", true);
        model.addAttribute("canAuthorTopics", true);
        model.addAttribute("canManageGroups", true);
        model.addAttribute("canManageBoards", false);
        model.addAttribute("canScoreBoards", false);
        model.addAttribute("canViewFacultyResults", false);
        model.addAttribute("canViewStudentResults", false);
        model.addAttribute("previewMode", true);
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

    private TopicDraftForm previewTopicForm() {
        PreviewTopic topic = TOPICS.getLast();
        TopicDraftForm form = new TopicDraftForm();
        form.setTitle(topic.title());
        form.setDescription(topic.description());
        form.setObjectives(topic.objectives());
        form.setStudentRequirements(topic.studentRequirements());
        form.setExpectedResults(topic.expectedResults());
        form.setCoSupervisorIds(List.of(12L));
        form.setVersion(topic.version());
        return form;
    }

    private PreviewPeriod previewPeriod(long id) {
        return PERIODS.stream()
                .filter(period -> period.id() == id)
                .findFirst()
                .orElse(PERIODS.getFirst());
    }

    private List<PreviewGroupPeriod> previewGroupPeriods(StudentPeriodPhase phase) {
        return GROUP_PERIODS.stream()
                .filter(period -> period.phase() == phase)
                .toList();
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
            String statusClass,
            long pendingTopicCount,
            long pendingRegistrationCount,
            long approvedTopicCount,
            boolean lecturerOpen,
            boolean active) {
        public String lecturerWindow() {
            return lecturerStart + " – " + lecturerEnd;
        }

        public String studentWindow() {
            return studentStart + " – " + studentEnd;
        }
    }

    public record PreviewTopic(
            int id,
            String title,
            String author,
            String department,
            String supervisors,
            String status,
            String statusClass,
            String description,
            String objectives,
            String studentRequirements,
            String expectedResults,
            long version,
            boolean draft) {
        public String supervisorNames() {
            return supervisors;
        }
    }

    public record PreviewLecturer(long id, String fullName, String username) {
    }

    public record PreviewGroupPeriod(
            long id,
            String name,
            String studentStart,
            String studentEnd,
            StudentPeriodPhase phase,
            boolean hasGroup,
            boolean hasPendingInvitation) {
        public String studentWindow() {
            return studentStart + " – " + studentEnd;
        }

        public String status() {
            return switch (phase) {
                case UPCOMING -> "Sắp đăng ký";
                case OPEN -> "Đang đăng ký";
                case ENDED -> "Đã kết thúc";
            };
        }

        public String statusClass() {
            return switch (phase) {
                case UPCOMING -> "bg-azure-lt";
                case OPEN -> "bg-green-lt";
                case ENDED -> "bg-secondary-lt";
            };
        }

        public boolean studentOpen() {
            return phase == StudentPeriodPhase.OPEN;
        }

        public boolean actionDisabled() {
            return !hasGroup && !hasPendingInvitation && !studentOpen();
        }

        public String actionLabel() {
            if (hasGroup) {
                return "Xem nhóm của tôi";
            }
            if (hasPendingInvitation) {
                return "Phản hồi lời mời";
            }
            return switch (phase) {
                case UPCOMING -> "Chưa đến thời gian";
                case OPEN -> "Tạo nhóm";
                case ENDED -> "Đã kết thúc";
            };
        }
    }

    public record PreviewGroup(
            long id,
            long periodId,
            long leaderId,
            long version,
            List<PreviewGroupMember> members,
            boolean canManage,
            boolean canEdit,
            boolean canLeave,
            boolean canDissolve,
            boolean studentOpen,
            PreviewGroupRegistration registration,
            int pendingInvitationCount) {
        public int reservedSlots() {
            return members.size() + pendingInvitationCount;
        }

        public boolean activeRegistration() {
            return registration != null;
        }
    }

    public record PreviewGroupRegistration(
            long id,
            String topicTitle,
            String status,
            String statusLabel,
            String statusClass,
            boolean accepted) {
    }

    public record PreviewGroupMember(
            long id,
            String fullName,
            String username,
            int slot,
            boolean leader) {
    }

    public record PreviewStudent(long id, String fullName, String username) {
    }

    public record PreviewInvitation(
            long id,
            long groupId,
            long periodId,
            String periodName,
            String studentName,
            String studentUsername,
            String invitedByName,
            int slot,
            String status,
            String statusClass,
            String createdAt,
            String respondedAt,
            long version,
            boolean groupActive,
            boolean canAccept,
            boolean canReject,
            boolean canCancel) {
    }

    public record PreviewRegistration(
            long id,
            long periodId,
            String periodName,
            long groupId,
            long topicId,
            String topicTitle,
            String topicAuthor,
            String topicDepartment,
            String supervisors,
            RegistrationStatus status,
            String statusLabel,
            String statusClass,
            String rejectionReason,
            String submittedAt,
            String decidedAt,
            String withdrawnAt,
            long version,
            boolean canWithdraw,
            boolean canDecide,
            boolean deanViewer) {
    }

    public record PreviewRegistrationRequest(
            long id,
            String groupCode,
            String leaderName,
            int memberCount,
            String topicTitle,
            String submittedAt,
            String status,
            String statusClass,
            String rejectionReason,
            boolean pending) {
    }
}

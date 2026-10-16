package vn.edu.topicregistration.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.integration.FixtureSupport;

/** T011: official announcement delivery and notification UX through a real browser. */
class NotificationFlowE2ETest extends BrowserTestSupport {
    private static final String ADMIN_USERNAME = "notification.e2e.admin";
    private static final String DEAN_USERNAME = "notification.e2e.dean";
    private static final String IT_STUDENT_USERNAME = "notification.e2e.student.it";
    private static final String CIVIL_STUDENT_USERNAME = "notification.e2e.student.civil";
    private static final String REVIEWER_USERNAME = "notification.e2e.reviewer";
    private static final String AUTHOR_USERNAME = "notification.e2e.author";
    private static final String SCHOOL_TITLE = "Lịch sinh hoạt đầu khóa";
    private static final String FACULTY_TITLE = "Thông báo riêng Khoa Công nghệ thông tin";

    private String password;
    private DataSource fixtureDataSource;
    private long itStudentId;
    private long itMemberOneId;
    private long itMemberTwoId;
    private long itFacultyId;
    private long reviewerId;
    private long authorId;
    private long deanId;

    @Override
    protected void prepareDatabase(FixtureSupport fixtures) {
        fixtureDataSource = fixtures.dataSource();
        password = requiredEnvironment("E2E_ACCOUNT_PASSWORD");
        String passwordHash = new BCryptPasswordEncoder().encode(password);
        FixtureSupport.OrganizationScope it = fixtures.existingOrganization(
                "Khoa Công nghệ Thông tin");
        itFacultyId = it.facultyId();
        FixtureSupport.OrganizationScope civil = fixtures.createOrganization("Khoa Xây dựng");
        fixtures.createIdentity(
                ADMIN_USERNAME, "Quản trị E2E", passwordHash, true, null,
                Set.of(RoleCode.ADMIN));
        deanId = fixtures.createIdentity(
                DEAN_USERNAME, "Trưởng khoa E2E", passwordHash, true, it,
                Set.of(RoleCode.DEAN)).accountId();
        reviewerId = fixtures.createIdentity(
                REVIEWER_USERNAME, "Giảng viên phản biện E2E", passwordHash, true, it,
                Set.of(RoleCode.LECTURER)).accountId();
        authorId = fixtures.createIdentity(
                AUTHOR_USERNAME, "Giảng viên tác giả E2E", passwordHash, true, it,
                Set.of(RoleCode.LECTURER)).accountId();
        itStudentId = fixtures.createIdentity(
                IT_STUDENT_USERNAME, "Sinh viên CNTT E2E", passwordHash, true, it,
                Set.of(RoleCode.STUDENT)).accountId();
        itMemberOneId = fixtures.createIdentity(
                "notification.e2e.member.one", "Thành viên Một E2E", passwordHash, true, it,
                Set.of(RoleCode.STUDENT)).accountId();
        itMemberTwoId = fixtures.createIdentity(
                "notification.e2e.member.two", "Thành viên Hai E2E", passwordHash, true, it,
                Set.of(RoleCode.STUDENT)).accountId();
        fixtures.createIdentity(
                CIVIL_STUDENT_USERNAME, "Sinh viên Xây dựng E2E", passwordHash, true, civil,
                Set.of(RoleCode.STUDENT));
    }

    @Test
    void publishOpenReadAndFacultyScopeRemainUsableWithKeyboardAndMobile() {
        login(IT_STUDENT_USERNAME);
        page().navigate(url("/notifications"));
        assertTrue(page().getByText("Bạn chưa có thông báo",
                new Page.GetByTextOptions().setExact(true)).isVisible());

        logout();
        login(ADMIN_USERNAME);
        String schoolAnnouncementUrl = createAndPublish(
                SCHOOL_TITLE,
                "Sinh viên theo dõi lịch sinh hoạt chính thức trên cổng học vụ.");

        logout();
        login(IT_STUDENT_USERNAME);
        page().setViewportSize(360, 800);
        Locator bell = page().locator(".notification-trigger");
        assertTrue(bell.getAttribute("aria-label").contains("1 thông báo chưa đọc"));
        bell.press("Enter");
        assertEquals("true", bell.getAttribute("aria-expanded"));
        assertTrue(page().locator(".notification-menu").getByText(
                SCHOOL_TITLE, new Locator.GetByTextOptions().setExact(true)).isVisible());
        assertNoHorizontalOverflow();

        Locator firstNotification = page().locator(
                ".notification-menu form:has-text(\"" + SCHOOL_TITLE + "\") button");
        firstNotification.press("Enter");
        page().waitForURL(schoolAnnouncementUrl);
        assertTrue(page().getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName(SCHOOL_TITLE).setExact(true)).isVisible());
        assertEquals(0, page().locator(".notification-count-badge").count());

        page().navigate(url("/notifications"));
        assertTrue(page().getByText("Đã đọc",
                new Page.GetByTextOptions().setExact(true)).first().isVisible());
        assertNoHorizontalOverflow();

        logout();
        login(DEAN_USERNAME);
        createAndPublish(
                FACULTY_TITLE,
                "Nội dung này chỉ gửi cho tài khoản đang hoạt động thuộc Khoa Công nghệ thông tin.");

        logout();
        login(CIVIL_STUDENT_USERNAME);
        page().navigate(url("/notifications"));
        Locator civilInbox = page().locator("#main-content");
        assertTrue(civilInbox.getByText(SCHOOL_TITLE,
                new Locator.GetByTextOptions().setExact(true)).isVisible());
        assertEquals(0, civilInbox.getByText(FACULTY_TITLE,
                new Locator.GetByTextOptions().setExact(true)).count());

        logout();
        login(IT_STUDENT_USERNAME);
        page().navigate(url("/notifications?status=UNREAD&category=ANNOUNCEMENT"));
        Locator itInbox = page().locator("#main-content");
        assertTrue(itInbox.getByText(FACULTY_TITLE,
                new Locator.GetByTextOptions().setExact(true)).isVisible());
        assertEquals(0, itInbox.getByText(SCHOOL_TITLE,
                new Locator.GetByTextOptions().setExact(true)).count());
        assertNoHorizontalOverflow();

        createUnavailableNotification();
        page().navigate(url("/notifications"));
        page().locator(".notification-open-form:has-text('Nhiệm vụ không còn khả dụng') button")
                .click();
        page().waitForURL(url("/notifications?target=unavailable"));
        assertTrue(page().getByText(
                "Nội dung của thông báo chưa sẵn sàng hoặc không còn khả dụng.",
                new Page.GetByTextOptions().setExact(false)).isVisible());
        assertNoHorizontalOverflow();

        long reviewerAssignmentId = createReviewerNotification();
        logout();
        login(REVIEWER_USERNAME);
        page().navigate(url("/notifications"));
        page().locator(".notification-open-form:has-text('Phân công phản biện E2E') button")
                .click();
        page().waitForURL(url("/lecturer/reviewers/" + reviewerAssignmentId));
        assertTrue(page().getByText("Đề tài phản biện E2E").isVisible());
    }

    private void createUnavailableNotification() {
        JdbcTemplate jdbc = new JdbcTemplate(fixtureDataSource);
        String eventKey = "TOPIC:987654:APPROVED";
        jdbc.update("""
                INSERT INTO notification_events(
                    event_key, category, event_type, title, body,
                    target_type, target_id, created_at)
                VALUES (?, 'ACADEMIC_EVENT', 'TOPIC_PROPOSAL_APPROVED', ?, ?,
                    'TOPIC', 987654, ?)
                """, eventKey, "Nhiệm vụ không còn khả dụng",
                "Đề tài này không còn mở được.", Timestamp.from(FIXED_NOW));
        Long eventId = jdbc.queryForObject(
                "SELECT id FROM notification_events WHERE event_key = ?",
                Long.class, eventKey);
        jdbc.update("""
                INSERT INTO user_notifications(event_id, recipient_id, delivered_at)
                VALUES (?, ?, ?)
                """, eventId, itStudentId, Timestamp.from(FIXED_NOW));
    }

    private long createReviewerNotification() {
        JdbcTemplate jdbc = new JdbcTemplate(fixtureDataSource);
        jdbc.update("INSERT INTO departments(faculty_id, name) VALUES (?, 'Bộ môn E2E')",
                itFacultyId);
        long departmentId = jdbc.queryForObject(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = 'Bộ môn E2E'",
                Long.class, itFacultyId);
        jdbc.update("UPDATE user_accounts SET department_id = ? WHERE id IN (?, ?)",
                departmentId, reviewerId, authorId);
        jdbc.update("""
                INSERT INTO registration_periods(
                    faculty_id, department_id, name, topic_type,
                    lecturer_start, lecturer_end, student_start, student_end,
                    reviewer_score_deadline, council_report_date)
                VALUES (?, ?, 'Đợt phản biện E2E', 'KLTN',
                    '2026-08-01', '2026-08-02', '2026-08-03', '2026-08-10',
                    '2026-09-12', '2026-09-20')
                """, itFacultyId, departmentId);
        long periodId = jdbc.queryForObject(
                "SELECT id FROM registration_periods WHERE name = 'Đợt phản biện E2E'",
                Long.class);
        jdbc.update("""
                INSERT INTO topics(period_id, department_id, author_id, title,
                    description, objectives, student_requirements, expected_results, status)
                VALUES (?, ?, ?, 'Đề tài phản biện E2E', 'Mô tả', 'Mục tiêu',
                    'Yêu cầu', 'Kết quả', 'PUBLISHED')
                """, periodId, departmentId, authorId);
        long topicId = jdbc.queryForObject(
                "SELECT id FROM topics WHERE title = 'Đề tài phản biện E2E'", Long.class);
        jdbc.update("INSERT INTO student_groups(period_id, leader_student_id) VALUES (?, ?)",
                periodId, itStudentId);
        long groupId = jdbc.queryForObject(
                "SELECT id FROM student_groups WHERE period_id = ?", Long.class, periodId);
        jdbc.update("""
                INSERT INTO group_memberships(group_id, period_id, student_id, slot)
                VALUES (?, ?, ?, 2), (?, ?, ?, 3)
                """, groupId, periodId, itMemberOneId,
                groupId, periodId, itMemberTwoId);
        jdbc.update("""
                INSERT INTO registrations(period_id, group_id, topic_id, status,
                    decision_actor_id, decided_at)
                VALUES (?, ?, ?, 'ACCEPTED', ?, '2026-08-11 00:00:00')
                """, periodId, groupId, topicId, deanId);
        long registrationId = jdbc.queryForObject(
                "SELECT id FROM registrations WHERE topic_id = ?", Long.class, topicId);
        jdbc.update("""
                INSERT INTO reviewer_assignments(registration_id, lecturer_id, deadline_date,
                    status, assigned_by, assigned_at)
                VALUES (?, ?, '2026-09-12', 'ASSIGNED', ?, '2026-09-10 00:00:00')
                """, registrationId, reviewerId, deanId);
        long assignmentId = jdbc.queryForObject(
                "SELECT id FROM reviewer_assignments WHERE registration_id = ?",
                Long.class, registrationId);
        jdbc.update("""
                INSERT INTO notification_events(event_key, category, event_type, title, body,
                    actor_id, target_type, target_id, created_at)
                VALUES (?, 'ACADEMIC_EVENT', 'REVIEWER_ASSIGNED',
                    'Phân công phản biện E2E', 'Mở nhiệm vụ phản biện', ?,
                    'REVIEWER_ASSIGNMENT', ?, ?)
                """, "REVIEWER:" + assignmentId + ":ASSIGNED", deanId,
                assignmentId, Timestamp.from(FIXED_NOW));
        long eventId = jdbc.queryForObject(
                "SELECT id FROM notification_events WHERE event_key = ?",
                Long.class, "REVIEWER:" + assignmentId + ":ASSIGNED");
        jdbc.update("""
                INSERT INTO user_notifications(event_id, recipient_id, delivered_at)
                VALUES (?, ?, ?)
                """, eventId, reviewerId, Timestamp.from(FIXED_NOW));
        return assignmentId;
    }

    private String createAndPublish(String title, String body) {
        page().navigate(url("/announcements/new"));
        page().getByLabel("Tiêu đề").fill(title);
        page().getByLabel("Nội dung").fill(body);
        button("Tạo bản nháp").click();
        assertFalse(page().url().endsWith("/announcements/new"));
        String detailUrl = page().url();
        button("Công bố").click();
        page().waitForURL(detailUrl);
        assertTrue(page().getByText("Đã công bố thông báo tới",
                new Page.GetByTextOptions().setExact(false)).isVisible());
        return detailUrl;
    }

    private void login(String username) {
        page().setViewportSize(1280, 800);
        page().navigate(url("/login"));
        page().getByLabel("Tên đăng nhập").fill(username);
        page().getByLabel("Mật khẩu", new Page.GetByLabelOptions().setExact(true))
                .fill(password);
        button("Đăng nhập").click();
        page().waitForURL(url("/"));
    }

    private void logout() {
        page().getByLabel("Mở menu tài khoản").click();
        button("Đăng xuất").click();
        page().waitForURL(url("/login?logout"));
    }

    private Locator button(String name) {
        return page().getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private void assertNoHorizontalOverflow() {
        double overflow = ((Number) page().evaluate("""
                () => document.documentElement.scrollWidth
                    - document.documentElement.clientWidth
                """)).doubleValue();
        assertTrue(overflow <= 1, () -> "Notification UI overflows by " + overflow + "px");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for browser tests");
        }
        return value;
    }
}

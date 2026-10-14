package vn.edu.topicregistration.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
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
    private static final String SCHOOL_TITLE = "Lịch sinh hoạt đầu khóa";
    private static final String FACULTY_TITLE = "Thông báo riêng Khoa Công nghệ thông tin";

    private String password;

    @Override
    protected void prepareDatabase(FixtureSupport fixtures) {
        password = requiredEnvironment("E2E_ACCOUNT_PASSWORD");
        String passwordHash = new BCryptPasswordEncoder().encode(password);
        FixtureSupport.OrganizationScope it = fixtures.createOrganization(
                "Khoa Công nghệ thông tin");
        FixtureSupport.OrganizationScope civil = fixtures.createOrganization("Khoa Xây dựng");
        fixtures.createIdentity(
                ADMIN_USERNAME, "Quản trị E2E", passwordHash, true, null,
                Set.of(RoleCode.ADMIN));
        fixtures.createIdentity(
                DEAN_USERNAME, "Trưởng khoa E2E", passwordHash, true, it,
                Set.of(RoleCode.DEAN));
        fixtures.createIdentity(
                IT_STUDENT_USERNAME, "Sinh viên CNTT E2E", passwordHash, true, it,
                Set.of(RoleCode.STUDENT));
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

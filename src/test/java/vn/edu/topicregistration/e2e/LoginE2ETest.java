package vn.edu.topicregistration.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.integration.FixtureSupport;

class LoginE2ETest extends BrowserTestSupport {
    private static final String USERNAME = "e2e-lecturer";
    private String password;

    @Override
    protected void prepareDatabase(FixtureSupport fixtures) {
        password = requiredEnvironment("E2E_ACCOUNT_PASSWORD");
        FixtureSupport.OrganizationScope scope = fixtures.createOrganization("Khoa E2E");
        fixtures.createIdentity(
                USERNAME,
                "Giảng viên E2E",
                new BCryptPasswordEncoder().encode(password),
                true,
                scope,
                Set.of(RoleCode.LECTURER));
    }

    @Test
    void provisionedAccountCanLoginThroughTheRealBrowser() {
        page().setViewportSize(1280, 800);
        page().navigate(url("/"));
        assertTrue(page().url().endsWith("/login"));

        page().getByLabel("Tên đăng nhập").fill(USERNAME);
        page().getByLabel("Mật khẩu", new Page.GetByLabelOptions().setExact(true)).fill(password);
        page().getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Đăng nhập")).click();

        page().waitForURL(url("/"));
        assertTrue(page().getByText("Quản lý đề tài sinh viên").first().isVisible());
        assertTrue(page().getByRole(com.microsoft.playwright.options.AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Tổng quan đề tài của tôi")).isVisible());
        assertEquals(0, page().getByText("Kết quả công bố").count());
        var overview = page().locator("#sidebar-menu .nav-item").first().boundingBox();
        var academicSection = page().locator("#sidebar-menu > .nav-section").nth(1).boundingBox();
        assertTrue(overview != null && academicSection != null
                && academicSection.y - (overview.y + overview.height) < 100,
                "Sidebar menu sections should not be pushed apart");
        Cookie sessionCookie = page().context().cookies().stream()
                .filter(cookie -> "JSESSIONID".equals(cookie.name))
                .findFirst()
                .orElseThrow();
        assertEquals(Boolean.TRUE, sessionCookie.httpOnly);
        assertEquals(SameSiteAttribute.LAX, sessionCookie.sameSite);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for browser tests");
        }
        return value;
    }
}

package vn.edu.topicregistration.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;

import vn.edu.topicregistration.config.TimeConfig;
import vn.edu.topicregistration.integration.FixtureSupport;
import vn.edu.topicregistration.integration.ReviewFixtureSupport;
import vn.edu.topicregistration.integration.ReviewFixtureSupport.ReviewFixture;

/** T008: browser journey from report upload and scoring through published student result. */
class ReviewResultsJourneyE2ETest extends BrowserTestSupport {
    @TempDir
    static Path reportRoot;

    private String password;
    private Path uploadFile;
    private ReviewFixture fixture;

    @Override
    protected void prepareDatabase(FixtureSupport fixtures) {
        password = requiredEnvironment("E2E_ACCOUNT_PASSWORD");
        fixture = new ReviewFixtureSupport(
                fixtures.dataSource(),
                Clock.fixed(FIXED_NOW, TimeConfig.BUSINESS_ZONE),
                reportRoot)
                .seed(password);
        uploadFile = reportRoot.resolve("e2e-upload-source.pdf");
        try {
            Files.write(uploadFile,
                    "%PDF-1.7\nT008 browser report".getBytes(StandardCharsets.US_ASCII));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the browser upload fixture", exception);
        }
    }

    @Test
    void completesReviewJourneyAndKeepsAnotherGroupResultPrivate() {
        login("student1.demo");
        assertTrue(link("Kết quả của tôi").isVisible());
        page().navigate(url("/reports/" + fixture.uploadRegistrationId()));
        page().getByLabel("File PDF").setInputFiles(uploadFile);
        button("Nộp báo cáo").click();
        page().waitForURL(url("/reports/" + fixture.uploadRegistrationId()));
        assertTrue(page().getByText("e2e-upload-source.pdf").isVisible());

        logout();
        login("dean.demo");
        assignReviewer(ReviewFixtureSupport.ACTIVE_TOPIC_ONE);
        assignReviewer(ReviewFixtureSupport.ACTIVE_TOPIC_TWO);

        logout();
        login("lecturer3.demo");
        submitReviewer(ReviewFixtureSupport.ACTIVE_TOPIC_ONE);
        submitReviewer(ReviewFixtureSupport.ACTIVE_TOPIC_TWO);

        logout();
        login("lecturer4.demo");
        assertTrue(link("Chấm điểm").isVisible());
        page().navigate(url("/lecturer/scoring/"
                + fixture.activeCouncilId() + "/" + fixture.activeRegistrationOneId()));
        int scoreRows = page().locator("input[type=number]").count();
        assertEquals(3, scoreRows);
        assertEquals(1, page().locator("form.card input[name='_csrf']").count(),
                "Phiếu chấm phải có CSRF token để POST từ trình duyệt");
        for (int index = 0; index < scoreRows; index++) {
            page().locator("input[type=number]").nth(index).fill("9.25");
            page().locator("textarea").nth(index).fill("Browser acceptance score");
        }
        assertPostRedirect("Lưu nháp toàn bộ", "/draft");
        assertEquals(3, page().getByText("Bản nháp", new Page.GetByTextOptions()
                .setExact(true)).count());
        acceptNextConfirmation();
        assertPostRedirect("Nộp toàn bộ", "/submit");
        page().waitForURL(url("/lecturer/scoring/"
                + fixture.activeCouncilId() + "/" + fixture.activeRegistrationOneId()));
        assertEquals(3, page().getByText("Đã nộp", new Page.GetByTextOptions()
                .setExact(true)).count());

        logout();
        login("dean.demo");
        assertTrue(link("Hội đồng").isVisible());
        assertTrue(link("Kết quả hội đồng").isVisible());
        page().navigate(url("/faculty/boards/" + fixture.activeCouncilId()));
        acceptNextConfirmation();
        assertPostRedirect("Tổng hợp kết quả", "/complete");
        page().navigate(url("/faculty/boards/" + fixture.activeCouncilId()));
        assertTrue(page().getByText("Đã hoàn tất", new Page.GetByTextOptions()
                .setExact(true)).isVisible());
        acceptNextConfirmation();
        button("Công bố kết quả").click();
        page().waitForURL(url("/faculty/results"));
        assertEquals(3, page().getByText(ReviewFixtureSupport.ACTIVE_TOPIC_ONE).count());

        logout();
        login("student1.demo");
        page().navigate(url("/student/results/" + fixture.activeRegistrationOneId()));
        assertEquals("8.31", page().locator(".display-1").textContent().strip());
        assertEquals(3, page().locator("tbody tr").count());

        logout();
        login("student2.demo");
        page().navigate(url("/student/results/" + fixture.activeRegistrationOneId()));
        assertEquals("8.31", page().locator(".display-1").textContent().strip());
        assertTrue(page().content().contains("Browser acceptance score"));

        logout();
        login("student4.demo");
        Response denied = page().navigate(
                url("/student/results/" + fixture.activeRegistrationOneId()));
        assertEquals(404, denied.status());
        assertFalse(page().content().contains(ReviewFixtureSupport.ACTIVE_TOPIC_ONE));
    }

    private void assignReviewer(String topicTitle) {
        page().navigate(url("/faculty/reviewers"));
        var row = page().locator("tbody tr").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText(topicTitle));
        row.locator("select").selectOption(new SelectOption().setLabel("Giảng viên Demo 3"));
        Response response = page().waitForResponse(
                candidate -> candidate.url().endsWith("/faculty/reviewers")
                        && "POST".equals(candidate.request().method()),
                () -> row.locator("button").click());
        assertEquals(303, response.status(), "Phân công GVPB: " + topicTitle);
    }

    private void submitReviewer(String topicTitle) {
        page().navigate(url("/lecturer/reviewers"));
        page().locator(".list-group-item").filter(
                new com.microsoft.playwright.Locator.FilterOptions().setHasText(topicTitle))
                .click();
        page().locator("input[type=file]").setInputFiles(uploadFile);
        int studentCount = page().locator("input[type=number]").count();
        assertEquals(3, studentCount);
        for (int index = 0; index < studentCount; index++) {
            page().locator("input[type=number]").nth(index).fill("7.00");
            page().locator("textarea").nth(index).fill("Đạt yêu cầu");
        }
        acceptNextConfirmation();
        assertPostRedirect("Nộp phản biện", "/submit");
        assertTrue(page().getByText("Đã nộp phản biện. Không thể sửa hoặc nộp lại.")
                .isVisible());
    }

    private void assertPostRedirect(String buttonName, String pathSuffix) {
        Response response = page().waitForResponse(
                candidate -> candidate.url().endsWith(pathSuffix)
                        && "POST".equals(candidate.request().method()),
                () -> button(buttonName).click());
        assertEquals(303, response.status(),
                buttonName + " chuyển tới " + page().url());
    }

    private void login(String username) {
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

    private void acceptNextConfirmation() {
        page().onceDialog(dialog -> dialog.accept());
    }

    private com.microsoft.playwright.Locator link(String name) {
        return page().getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private com.microsoft.playwright.Locator button(String name) {
        return page().getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for browser tests");
        }
        return value;
    }
}

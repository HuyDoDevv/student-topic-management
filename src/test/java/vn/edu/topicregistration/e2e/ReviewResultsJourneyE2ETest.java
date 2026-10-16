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
        login("lecturer4.demo");
        assertTrue(link("Chấm điểm").isVisible());
        page().navigate(url("/lecturer/scoring/"
                + fixture.activeCouncilId() + "/" + fixture.activeRegistrationOneId()));
        int scoreRows = page().locator("input[type=number]").count();
        assertEquals(3, scoreRows);
        for (int index = 0; index < scoreRows; index++) {
            page().locator("input[type=number]").nth(index).fill("9.25");
            page().locator("textarea").nth(index).fill("Browser acceptance score");
        }
        acceptNextConfirmation();
        button("Nộp toàn bộ").click();
        page().waitForURL(url("/lecturer/scoring/"
                + fixture.activeCouncilId() + "/" + fixture.activeRegistrationOneId()));
        assertEquals(3, page().getByText("Đã nộp", new Page.GetByTextOptions()
                .setExact(true)).count());

        logout();
        login("lecturer1.demo");
        page().navigate(url("/faculty/boards/" + fixture.activeCouncilId()));
        acceptNextConfirmation();
        button("Tổng hợp kết quả").click();
        page().waitForURL(url("/faculty/boards/" + fixture.activeCouncilId()));
        assertTrue(page().getByText("Đã hoàn tất", new Page.GetByTextOptions()
                .setExact(true)).isVisible());

        logout();
        login("dean.demo");
        assertTrue(link("Hội đồng").isVisible());
        assertTrue(link("Kết quả hội đồng").isVisible());
        page().navigate(url("/faculty/boards/" + fixture.activeCouncilId()));
        acceptNextConfirmation();
        button("Công bố kết quả").click();
        page().waitForURL(url("/faculty/results"));
        assertEquals(3, page().getByText(ReviewFixtureSupport.ACTIVE_TOPIC_ONE).count());

        logout();
        login("student1.demo");
        page().navigate(url("/student/results/" + fixture.activeRegistrationOneId()));
        assertEquals("8.75", page().locator(".display-1").textContent().strip());
        assertEquals(3, page().locator("tbody tr").count());

        logout();
        login("student2.demo");
        page().navigate(url("/student/results/" + fixture.activeRegistrationOneId()));
        assertEquals("8.5", page().locator(".display-1").textContent().strip());
        assertFalse(page().content().contains("Browser acceptance score"));

        logout();
        login("student4.demo");
        Response denied = page().navigate(
                url("/student/results/" + fixture.activeRegistrationOneId()));
        assertEquals(404, denied.status());
        assertFalse(page().content().contains(ReviewFixtureSupport.ACTIVE_TOPIC_ONE));
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

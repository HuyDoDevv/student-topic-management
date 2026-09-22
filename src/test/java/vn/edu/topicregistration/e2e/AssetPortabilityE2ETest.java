package vn.edu.topicregistration.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;

/** T062: executable JAR, local assets, responsive layout and basic accessibility. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssetPortabilityE2ETest {
    private static final String[] MAIN_ROUTES = {
            "/login", "/dashboard", "/dashboard?role=dean", "/dashboard?role=lecturer",
            "/dashboard?role=admin", "/periods", "/periods/new", "/periods/1",
            "/periods/1/edit", "/topics", "/periods/1/topics",
            "/periods/1/topics/new", "/topics/101", "/topics/101/edit",
            "/periods/1/topics/review", "/groups", "/periods/1/groups/new",
            "/groups/1", "/group-invitations", "/groups/1/registrations/new",
            "/registrations/1", "/registrations/1/decision",
            "/admin/accounts/students", "/admin/accounts/lecturers",
            "/admin/accounts/deans", "/admin/accounts/new"
    };
    private static final int[][] VIEWPORTS = {
            {360, 800}, {768, 900}, {1280, 900}
    };

    @TempDir
    static Path externalWorkingDirectory;

    private final List<String> applicationOutput = new CopyOnWriteArrayList<>();
    private final List<String> browserRequests = new CopyOnWriteArrayList<>();
    private Process application;
    private Thread outputReader;
    private Playwright playwright;
    private Browser browser;
    private Page page;
    private Path executableJar;
    private String baseUrl;

    @BeforeAll
    void startPackagedApplicationOutsideTheProject() throws Exception {
        Path projectDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        executableJar = projectDirectory.resolve("target/topic-registration.jar");
        assertTrue(Files.isRegularFile(executableJar),
                "Build the executable JAR before running T062");
        assertFalse(externalWorkingDirectory.startsWith(projectDirectory),
                "The packaged application must run outside the project directory");

        int port = availablePort();
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        application = new ProcessBuilder(
                java.toString(),
                "-jar",
                executableJar.toString(),
                "--spring.profiles.active=preview",
                "--server.port=" + port)
                .directory(externalWorkingDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        outputReader = Thread.ofVirtual().name("t062-jar-output").start(this::captureOutput);
        baseUrl = "http://127.0.0.1:" + port;
        awaitApplication();

        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @BeforeEach
    void openIsolatedBrowserPage() {
        browserRequests.clear();
        page = browser.newPage();
        page.onRequest(request -> browserRequests.add(request.url()));
    }

    @AfterEach
    void closeIsolatedBrowserPage() {
        if (page != null) {
            page.close();
            page = null;
        }
    }

    @AfterAll
    void stopPackagedApplicationAndBrowser() throws Exception {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (application != null) {
            application.destroy();
            if (!application.waitFor(5, TimeUnit.SECONDS)) {
                application.destroyForcibly();
                application.waitFor(5, TimeUnit.SECONDS);
            }
        }
        if (outputReader != null) {
            outputReader.join(Duration.ofSeconds(5));
        }
    }

    @Test
    void jarContainsNoticesAndServesOnlyPackagedLocalAssets() throws Exception {
        try (JarFile jar = new JarFile(executableJar.toFile())) {
            for (String entry : List.of(
                    "META-INF/THIRD_PARTY_NOTICES.md",
                    "META-INF/licenses/tabler-1.4.0-LICENSE",
                    "META-INF/licenses/bootstrap-LICENSE",
                    "META-INF/licenses/popper-LICENSE",
                    "BOOT-INF/classes/static/vendor/tabler/1.4.0/css/tabler.min.css",
                    "BOOT-INF/classes/static/vendor/tabler/1.4.0/js/tabler.min.js")) {
                assertNotNull(jar.getJarEntry(entry), () -> "Missing packaged entry: " + entry);
            }
        }

        Response response = navigate("/periods");
        assertNotNull(response);
        assertEquals(200, response.status());
        page.waitForLoadState();
        assertTrue(browserRequests.stream().anyMatch(
                request -> request.endsWith("/vendor/tabler/1.4.0/css/tabler.min.css")));
        assertTrue(browserRequests.stream().anyMatch(
                request -> request.endsWith("/vendor/tabler/1.4.0/js/tabler.min.js")));
        assertTrue(browserRequests.stream()
                .filter(request -> request.startsWith("http://") || request.startsWith("https://"))
                .allMatch(request -> request.startsWith(baseUrl + "/")),
                () -> "External browser request detected: " + browserRequests);
        assertEquals(List.of(), page.evaluate("""
                () => [...document.querySelectorAll('[src], [href]')]
                  .map(element => element.getAttribute('src') || element.getAttribute('href'))
                  .filter(value => value.startsWith('http://') || value.startsWith('https://'))
                """), "Rendered assets and links must not depend on a CDN or source-tree URL");
    }

    @Test
    void mainPagesDoNotOverflowAtRequiredViewports() {
        for (int[] viewport : VIEWPORTS) {
            page.setViewportSize(viewport[0], viewport[1]);
            for (String route : MAIN_ROUTES) {
                Response response = navigate(route);
                assertNotNull(response, () -> "No response for " + route);
                assertTrue(response.status() < 400,
                        () -> route + " returned " + response.status());
                double horizontalOverflow = ((Number) page.evaluate("""
                        () => Math.max(
                            document.documentElement.scrollWidth - document.documentElement.clientWidth,
                            document.body.scrollWidth - document.body.clientWidth)
                        """)).doubleValue();
                assertTrue(horizontalOverflow <= 1,
                        () -> route + " overflows by " + horizontalOverflow
                                + "px at width " + viewport[0]);
            }
        }
    }

    @Test
    void wideTablesStayInsideScrollContainers() {
        page.setViewportSize(768, 900);
        navigate("/periods");
        assertTrue((Boolean) page.locator(".table-responsive").evaluate("""
                element => {
                  const style = getComputedStyle(element);
                  return ['auto', 'scroll'].includes(style.overflowX)
                      && element.scrollWidth > element.clientWidth;
                }
                """), "The wide period table must scroll inside its own container");
        assertNoDocumentOverflow("/periods", 768);

        page.setViewportSize(360, 800);
        navigate("/admin/accounts/new");
        assertTrue((Boolean) page.locator(".table-responsive").evaluate("""
                element => ['auto', 'scroll'].includes(getComputedStyle(element).overflowX)
                """), "Table containers must retain local horizontal scrolling on mobile");
        assertNoDocumentOverflow("/admin/accounts/new", 360);
    }

    @Test
    void navigationFormsStatesAndFeedbackRemainAccessibleWithoutColorAlone() {
        page.setViewportSize(360, 800);
        navigate("/periods");
        page.keyboard().press("Tab");
        assertEquals("#main-content", page.locator(":focus").getAttribute("href"));
        page.keyboard().press("Enter");
        assertEquals("main-content", page.locator(":focus").getAttribute("id"));

        var mobileMenu = page.getByLabel(
                "Mở menu", new Page.GetByLabelOptions().setExact(true));
        mobileMenu.press("Enter");
        assertEquals("true", mobileMenu.getAttribute("aria-expanded"));
        assertTrue(page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Đợt đăng ký").setExact(true)).isVisible());

        assertEquals(List.of(), page.evaluate("""
                () => [...document.querySelectorAll(
                    'input:not([type="hidden"]), select, textarea')]
                  .filter(element => {
                    if (element.getAttribute('aria-label')) return false;
                    if (!element.id) return true;
                    return !document.querySelector(`label[for="${CSS.escape(element.id)}"]`);
                  })
                  .map(element => element.id || element.name || element.tagName)
                """), "Every form control must have an accessible label");

        page.getByLabel("Xem thông báo").press("Enter");
        assertTrue(page.getByText("Chưa có thông báo để hiển thị.").isVisible());
        assertTrue(page.locator(".badge").allTextContents().stream()
                .map(String::strip)
                .noneMatch(String::isEmpty), "Badges must include a visible text status");
        assertTrue(page.getByText("Đã kết thúc", new Page.GetByTextOptions().setExact(true))
                .last().isVisible(), "Status must be conveyed by text, not color alone");

        navigate("/periods/new");
        assertEquals(List.of(), page.evaluate("""
                () => [...document.querySelectorAll('button, a[href]')]
                  .filter(element => element.getClientRects().length > 0)
                  .filter(element => !(element.innerText || '').trim()
                      && !element.getAttribute('aria-label')
                      && !element.getAttribute('title'))
                  .map(element => element.outerHTML.slice(0, 120))
                """), "Visible actions must expose an accessible name");

        Response error = navigate("/not-a-page");
        assertNotNull(error);
        assertEquals(404, error.status());
        assertEquals("404", page.locator(".error-code").textContent().strip());
        assertTrue(page.locator("h1").isVisible());
    }

    private void assertNoDocumentOverflow(String route, int width) {
        double overflow = ((Number) page.evaluate("""
                () => document.documentElement.scrollWidth
                    - document.documentElement.clientWidth
                """)).doubleValue();
        assertTrue(overflow <= 1,
                () -> route + " overflows by " + overflow + "px at width " + width);
    }

    private void awaitApplication() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url("/login")))
                .timeout(Duration.ofSeconds(1))
                .GET()
                .build();
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            if (!application.isAlive()) {
                throw new IllegalStateException("Packaged application exited early:\n" + output());
            }
            try {
                HttpResponse<Void> response = client.send(
                        request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException ignored) {
                // The socket is not ready yet.
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Timed out waiting for packaged application:\n" + output());
    }

    private void captureOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                application.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(applicationOutput::add);
        } catch (IOException exception) {
            applicationOutput.add("Could not read application output: " + exception.getMessage());
        }
    }

    private String output() {
        int first = Math.max(0, applicationOutput.size() - 80);
        return String.join(System.lineSeparator(), applicationOutput.subList(
                first, applicationOutput.size()));
    }

    private String url(String path) {
        return baseUrl + path;
    }

    private Response navigate(String path) {
        return page.navigate(url(path), new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(10_000));
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

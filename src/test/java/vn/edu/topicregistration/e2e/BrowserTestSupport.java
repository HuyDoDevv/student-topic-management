package vn.edu.topicregistration.e2e;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.MapPropertySource;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import vn.edu.topicregistration.TopicRegistrationApplication;
import vn.edu.topicregistration.integration.FixtureSupport;
import vn.edu.topicregistration.integration.MySqlTestSupport;
import vn.edu.topicregistration.shared.time.TimeConfig;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BrowserTestSupport {
    private static final Instant FIXED_NOW = Instant.parse("2026-09-10T01:00:00Z");

    private ConfigurableApplicationContext applicationContext;
    private Playwright playwright;
    private Browser browser;
    private Page page;
    private String baseUrl;

    @BeforeAll
    final void startApplicationAndBrowser() {
        MySqlTestSupport database = MySqlTestSupport.fromEnvironment();
        database.resetAndMigrate();
        FixtureSupport fixtures = new FixtureSupport(
                database.dataSource(), Clock.fixed(FIXED_NOW, TimeConfig.BUSINESS_ZONE));
        prepareDatabase(fixtures);

        SpringApplication application = new SpringApplication(
                TopicRegistrationApplication.class, BrowserClockConfiguration.class);
        application.addInitializers(context -> context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("browser-test", database.springApplicationProperties())));
        applicationContext = application.run();
        int port = ((WebServerApplicationContext) applicationContext).getWebServer().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        page = browser.newPage();
    }

    @AfterAll
    final void stopApplicationAndBrowser() {
        if (page != null) {
            page.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (applicationContext != null) {
            applicationContext.close();
        }
    }

    protected abstract void prepareDatabase(FixtureSupport fixtures);

    protected final Page page() {
        return page;
    }

    protected final String url(String path) {
        return baseUrl + path;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BrowserClockConfiguration {
        @Bean
        @Primary
        Clock browserTestClock() {
            return Clock.fixed(FIXED_NOW, TimeConfig.BUSINESS_ZONE);
        }
    }
}

package vn.edu.topicregistration.integration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class MySqlTestSupport {
    private static final String URL_VARIABLE = "TEST_DATABASE_URL";
    private static final String USERNAME_VARIABLE = "TEST_DATABASE_USERNAME";
    private static final String PASSWORD_VARIABLE = "TEST_DATABASE_PASSWORD";

    private final String url;
    private final String username;
    private final String password;
    private final DataSource dataSource;

    private MySqlTestSupport(String url, String username, String password) {
        this.url = assertSafeTestDatabaseUrl(url);
        this.username = requireVariable(USERNAME_VARIABLE, username);
        this.password = requireVariable(PASSWORD_VARIABLE, password);
        this.dataSource = new DriverManagerDataSource(this.url, this.username, this.password);
    }

    public static MySqlTestSupport fromEnvironment() {
        return new MySqlTestSupport(
                requireVariable(URL_VARIABLE, System.getenv(URL_VARIABLE)),
                System.getenv(USERNAME_VARIABLE),
                System.getenv(PASSWORD_VARIABLE));
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public String databaseName() {
        return databaseName(url);
    }

    public Map<String, Object> springApplicationProperties() {
        return Map.of(
                "spring.profiles.active", "local",
                "spring.datasource.url", url,
                "spring.datasource.username", username,
                "spring.datasource.password", password,
                "server.address", "127.0.0.1",
                "server.port", "0",
                "spring.main.banner-mode", "off");
    }

    public void resetAndMigrate() {
        assertSafeTestDatabaseUrl(url);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    public static String assertSafeTestDatabaseUrl(String url) {
        String value = requireVariable(URL_VARIABLE, url);
        if (!value.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("TEST_DATABASE_URL must use jdbc:mysql");
        }
        String database = databaseName(value).toLowerCase(Locale.ROOT);
        if (!database.contains("test")) {
            throw new IllegalArgumentException("Refusing to reset a database whose name does not contain 'test'");
        }
        return value;
    }

    private static String databaseName(String jdbcUrl) {
        try {
            URI uri = new URI(jdbcUrl.substring("jdbc:".length()));
            String path = uri.getPath();
            if (path == null || path.length() <= 1 || path.substring(1).contains("/")) {
                throw new IllegalArgumentException("TEST_DATABASE_URL must name exactly one database");
            }
            return path.substring(1);
        } catch (URISyntaxException | IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException("TEST_DATABASE_URL is invalid", exception);
        }
    }

    private static String requireVariable(String variable, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is required for MySQL tests");
        }
        return value;
    }
}

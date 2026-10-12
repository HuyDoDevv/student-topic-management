package vn.edu.topicregistration.integration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public final class MySqlTestSupport {
    private static final String URL_VARIABLE = "TEST_DATABASE_URL";
    private static final String USERNAME_VARIABLE = "TEST_DATABASE_USERNAME";
    private static final String PASSWORD_VARIABLE = "TEST_DATABASE_PASSWORD";
    private static final String SAFE_HOST = "127.0.0.1";
    private static final int SAFE_PORT = 3307;
    private static final String SAFE_DATABASE = "topic_registration_test";
    private static final String SAFE_AUTHORITY = SAFE_HOST + ":" + SAFE_PORT;
    private static final String SAFE_PATH = "/" + SAFE_DATABASE;

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
        return databaseName(parseJdbcUri(url));
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
        URI uri = parseJdbcUri(value);
        String database = databaseName(uri);
        if (!"mysql".equals(uri.getScheme())
                || !SAFE_AUTHORITY.equals(uri.getRawAuthority())
                || !SAFE_HOST.equals(uri.getHost())
                || uri.getPort() != SAFE_PORT
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || !SAFE_PATH.equals(uri.getRawPath())
                || !SAFE_DATABASE.equals(database)
                || (uri.getRawQuery() != null && uri.getRawQuery().isBlank())) {
            throw new IllegalArgumentException(
                    "TEST_DATABASE_URL must target the approved local test database");
        }
        return value;
    }

    private static URI parseJdbcUri(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("TEST_DATABASE_URL must use jdbc:mysql");
        }
        try {
            return new URI(jdbcUrl.substring("jdbc:".length()));
        } catch (URISyntaxException | IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException("TEST_DATABASE_URL is invalid", exception);
        }
    }

    private static String databaseName(URI uri) {
        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.length() <= 1 || rawPath.substring(1).contains("/")) {
            throw new IllegalArgumentException("TEST_DATABASE_URL must name exactly one database");
        }
        return rawPath.substring(1);
    }

    private static String requireVariable(String variable, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is required for MySQL tests");
        }
        return value;
    }
}

package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.integration.MySqlTestSupport;

class TestDatabaseSafetyTest {
    @Test
    void acceptsOnlyExplicitMySqlTestDatabases() {
        String safeUrl = "jdbc:mysql://127.0.0.1:3307/topic_registration_test";
        String safeUrlWithParameters = safeUrl + "?connectionTimeZone=UTC&useSSL=false";
        assertEquals(safeUrl, MySqlTestSupport.assertSafeTestDatabaseUrl(safeUrl));
        assertEquals(
                safeUrlWithParameters,
                MySqlTestSupport.assertSafeTestDatabaseUrl(safeUrlWithParameters));

        List.of(
                "jdbc:mysql://localhost:3306/topic_registration_test",
                "jdbc:mysql://[::1]:3306/topic_registration_test",
                "jdbc:mysql://10.0.0.5:3306/topic_registration_test",
                "jdbc:mysql://127.0.0.1.example.com:3306/topic_registration_test",
                "jdbc:mysql://127.0.0.1/topic_registration_test",
                "jdbc:mysql://127.0.0.1:3306/topic_registration_test",
                "jdbc:mysql://127.0.0.1:3306/topic_registration_local",
                "jdbc:mysql://127.0.0.1:3306/contest_prod",
                "jdbc:mysql://127.0.0.1:3306/topic_registration_test_backup",
                "jdbc:mysql://127.0.0.1:3306/test",
                "jdbc:mysql://127.0.0.1:3306/topic_registration_%74est",
                "jdbc:mysql://127.0.0.1:3306/topic_registration_test/",
                "jdbc:mysql://127.0.0.1:3306/other/../topic_registration_test",
                "jdbc:mysql://user@127.0.0.1:3306/topic_registration_test",
                "jdbc:mysql://127.0.0.1:3306/topic_registration_test#fragment",
                "jdbc:mysql://127.0.0.1:3306/topic_registration_test?",
                "jdbc:mysql://127.0.0.1:3306/topic registration test",
                "jdbc:mysql://",
                "jdbc:postgresql://127.0.0.1:3306/topic_registration_test")
                .forEach(unsafeUrl -> assertThrows(
                        IllegalArgumentException.class,
                        () -> MySqlTestSupport.assertSafeTestDatabaseUrl(unsafeUrl),
                        unsafeUrl));
        assertThrows(IllegalStateException.class, () -> MySqlTestSupport.assertSafeTestDatabaseUrl(" "));
    }
}

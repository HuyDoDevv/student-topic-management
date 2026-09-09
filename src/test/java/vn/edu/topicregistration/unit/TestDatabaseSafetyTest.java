package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import vn.edu.topicregistration.integration.MySqlTestSupport;

class TestDatabaseSafetyTest {
    @Test
    void acceptsOnlyExplicitMySqlTestDatabases() {
        String safeUrl = "jdbc:mysql://127.0.0.1:3306/topic_registration_test?connectionTimeZone=UTC";
        assertEquals(safeUrl, MySqlTestSupport.assertSafeTestDatabaseUrl(safeUrl));

        assertThrows(IllegalArgumentException.class, () -> MySqlTestSupport.assertSafeTestDatabaseUrl(
                "jdbc:mysql://127.0.0.1:3306/topic_registration_local"));
        assertThrows(IllegalArgumentException.class, () -> MySqlTestSupport.assertSafeTestDatabaseUrl(
                "jdbc:postgresql://127.0.0.1/topic_registration_test"));
        assertThrows(IllegalStateException.class, () -> MySqlTestSupport.assertSafeTestDatabaseUrl(" "));
    }
}

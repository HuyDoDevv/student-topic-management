package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ReviewSchemaContractTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/V8__implement_review_and_results.sql");
    private static final Path DIRECT_GRADING_MIGRATION = Path.of(
            "src/main/resources/db/migration/V10__support_direct_grading_assignments.sql");

    @Test
    void v8AddsLifecycleAndConsistencyGuardsToPlaceholderTables() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertAll(
                () -> assertTrue(sql.contains("current_registration_guard")),
                () -> assertTrue(sql.contains("uq_project_reports_current_registration")),
                () -> assertTrue(sql.contains("department_id")),
                () -> assertTrue(sql.contains("chk_councils_status")),
                () -> assertTrue(sql.contains("uq_council_topics_registration")),
                () -> assertTrue(sql.contains("chk_evaluations_score")),
                () -> assertTrue(sql.contains("chk_evaluations_submission")),
                () -> assertTrue(sql.contains("chk_final_results_lifecycle")),
                () -> assertTrue(sql.contains("chk_final_results_score")));
    }

    @Test
    void v10DistinguishesDirectGradingFromRegularCouncils() throws IOException {
        String sql = Files.readString(DIRECT_GRADING_MIGRATION);

        assertAll(
                () -> assertTrue(sql.contains("grading_mode")),
                () -> assertTrue(sql.contains("COUNCIL")),
                () -> assertTrue(sql.contains("DIRECT")),
                () -> assertTrue(sql.contains("chk_councils_grading_mode_officers")),
                () -> assertTrue(sql.contains("trg_councils_add_officers")),
                () -> assertTrue(sql.contains("NEW.secretary_id <> NEW.chair_id")));
    }
}

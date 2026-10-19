package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ReviewSchemaContractTest {
    private static final Path BASELINE = Path.of("database/V1__baseline.sql");

    @Test
    void baselineContainsTheFinalReviewAndResultSchema() throws IOException {
        String sql = Files.readString(BASELINE);

        assertAll(
                () -> assertTrue(sql.contains("CREATE TABLE `project_reports`")),
                () -> assertTrue(sql.contains("uq_project_reports_current_registration")),
                () -> assertTrue(sql.contains("CREATE TABLE `councils`")),
                () -> assertTrue(sql.contains("chk_councils_grading_mode_officers")),
                () -> assertTrue(sql.contains("CREATE TABLE `evaluations`")),
                () -> assertTrue(sql.contains("uq_evaluations_student_actor_component")),
                () -> assertTrue(sql.contains("fk_evaluations_registration_student")),
                () -> assertTrue(sql.contains("CREATE TABLE `final_results`")),
                () -> assertTrue(sql.contains("fk_final_results_registration_student")));
    }

    @Test
    void baselineContainsTheFinalIdentityAndScopeSchema() throws IOException {
        String sql = Files.readString(BASELINE);

        assertAll(
                () -> assertTrue(sql.contains("CREATE TABLE `faculties`")),
                () -> assertTrue(sql.contains("CREATE TABLE `departments`")),
                () -> assertTrue(sql.contains("fk_periods_department_faculty")),
                () -> assertTrue(sql.contains("CREATE TABLE `student_profiles`")),
                () -> assertTrue(sql.contains("uq_student_profiles_student_code")),
                () -> assertTrue(sql.contains("must_change_password")),
                () -> assertTrue(sql.contains("security_version")),
                () -> assertTrue(sql.contains("Khoa Công nghệ Thông tin")));
    }

    @Test
    void baselineHasOnlyTheApprovedLocalBootstrapAccount() throws IOException {
        String sql = Files.readString(BASELINE);

        assertAll(
                () -> assertTrue(sql.contains("'admin'")),
                () -> assertTrue(sql.contains("'ADMIN'")),
                () -> assertTrue(sql.contains("$2y$10$")),
                () -> assertFalse(sql.contains("admin.demo")),
                () -> assertFalse(sql.contains("DROP TABLE")),
                () -> assertFalse(sql.contains("DEFINER=")));
    }
}

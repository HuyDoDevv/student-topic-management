package vn.edu.topicregistration.seed;

import vn.edu.topicregistration.util.ProjectTime;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * Creates synthetic data for local UI review when explicitly enabled.
 *
 * <p>The fixture uses stable demo usernames and names as identifiers. Existing rows are never
 * deleted or updated, so restarting the application does not reset passwords, workflow states,
 * group membership, or the dates recorded by the first successful seed.</p>
 */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "demo.seed", name = "enabled", havingValue = "true")
public class LocalDemoSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDemoSeeder.class);

    private static final String FACULTY_NAME = "[DEMO] Khoa Công nghệ Thông tin";
    private static final String SOFTWARE_DEPARTMENT = "[DEMO] Công nghệ phần mềm";
    private static final String INFORMATION_SYSTEMS_DEPARTMENT = "[DEMO] Hệ thống thông tin";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ProjectTime projectTime;
    private final DemoSeedProperties properties;

    public LocalDemoSeeder(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            ProjectTime projectTime,
            DemoSeedProperties properties) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.projectTime = projectTime;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String password = properties.password();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "DEMO_SEED_PASSWORD is required when DEMO_SEED_ENABLED=true");
        }

        if (exists(
                "SELECT COUNT(*) FROM user_accounts WHERE username = ?",
                "dean.demo")) {
            log.info(
                    "Local demo fixture already exists; preserving its current dates, "
                            + "memberships, and workflow states.");
            return;
        }

        String passwordHash = passwordEncoder.encode(password);
        long facultyId = faculty(FACULTY_NAME);
        long softwareDepartmentId = department(SOFTWARE_DEPARTMENT, facultyId);
        long informationSystemsDepartmentId = department(
                INFORMATION_SYSTEMS_DEPARTMENT, facultyId);

        long deanId = account(
                "dean.demo", "Trưởng khoa Demo", softwareDepartmentId, passwordHash,
                "DEAN", "LECTURER");
        long lecturer1Id = account(
                "lecturer1.demo", "Giảng viên Demo 1", softwareDepartmentId, passwordHash,
                "LECTURER");
        long lecturer2Id = account(
                "lecturer2.demo", "Giảng viên Demo 2", softwareDepartmentId, passwordHash,
                "LECTURER");
        long lecturer3Id = account(
                "lecturer3.demo", "Giảng viên Demo 3", informationSystemsDepartmentId,
                passwordHash, "LECTURER");

        long[] students = new long[7];
        for (int index = 0; index < students.length; index++) {
            int number = index + 1;
            long departmentId = number <= 4
                    ? softwareDepartmentId
                    : informationSystemsDepartmentId;
            students[index] = account(
                    "student" + number + ".demo",
                    "Sinh viên Demo " + number,
                    departmentId,
                    passwordHash,
                    "STUDENT");
        }

        LocalDate today = projectTime.today();
        PeriodWindow studentOpen = period(
                facultyId,
                "[DEMO] Đồ án môn học - Đang đăng ký",
                "COURSE",
                today.minusDays(14),
                today.minusDays(7),
                today.minusDays(5),
                today.plusDays(14),
                null,
                null);
        PeriodWindow lecturerOpen = period(
                facultyId,
                "[DEMO] Nghiên cứu khoa học - Đang đề xuất",
                "NCKH",
                today.minusDays(2),
                today.plusDays(5),
                today.plusDays(7),
                today.plusDays(21),
                null,
                null);
        PeriodWindow betweenWindows = period(
                facultyId,
                "[DEMO] Tiểu luận chuyên ngành - Chờ đăng ký",
                "TLCN",
                today.minusDays(15),
                today.minusDays(7),
                today.plusDays(2),
                today.plusDays(16),
                today.plusDays(40),
                null);
        PeriodWindow registrationClosed = period(
                facultyId,
                "[DEMO] Khóa luận tốt nghiệp - Đã đóng",
                "KLTN",
                today.minusDays(60),
                today.minusDays(50),
                today.minusDays(45),
                today.minusDays(30),
                today.minusDays(10),
                today.minusDays(5));

        long sharedTopicId = topic(
                studentOpen.id(), softwareDepartmentId, lecturer1Id,
                "[DEMO] Hệ thống quản lý đăng ký đề tài", "PUBLISHED");
        supervisor(sharedTopicId, lecturer1Id, 1);
        supervisor(sharedTopicId, lecturer2Id, 2);

        long availableTopicId = topic(
                studentOpen.id(), softwareDepartmentId, lecturer2Id,
                "[DEMO] Cổng theo dõi tiến độ học tập", "PUBLISHED");
        supervisor(availableTopicId, lecturer2Id, 1);

        long draftTopicId = topic(
                lecturerOpen.id(), informationSystemsDepartmentId, lecturer3Id,
                "[DEMO] Phân tích dữ liệu học tập", "DRAFT");
        supervisor(draftTopicId, lecturer3Id, 1);

        long pendingTopicId = topic(
                lecturerOpen.id(), informationSystemsDepartmentId, lecturer3Id,
                "[DEMO] Trợ lý tra cứu quy chế", "PENDING_APPROVAL");
        supervisor(pendingTopicId, lecturer3Id, 1);

        long approvedTopicId = topic(
                betweenWindows.id(), softwareDepartmentId, lecturer1Id,
                "[DEMO] Ứng dụng quản lý phòng học", "APPROVED");
        supervisor(approvedTopicId, lecturer1Id, 1);

        long closedTopicId = topic(
                registrationClosed.id(), softwareDepartmentId, lecturer2Id,
                "[DEMO] Nền tảng quản lý khóa luận", "PUBLISHED");
        supervisor(closedTopicId, lecturer2Id, 1);

        CreatedRow groupOfThree = group(studentOpen.id(), students[0]);
        if (groupOfThree.created()) {
            membership(groupOfThree.id(), studentOpen.id(), students[1], 2);
            membership(groupOfThree.id(), studentOpen.id(), students[2], 3);
        }
        CreatedRow alternativeGroupOfThree = group(studentOpen.id(), students[3]);
        if (alternativeGroupOfThree.created()) {
            membership(alternativeGroupOfThree.id(), studentOpen.id(), students[4], 2);
            membership(alternativeGroupOfThree.id(), studentOpen.id(), students[6], 3);
        }
        group(studentOpen.id(), students[5]);

        registration(
                studentOpen, groupOfThree.id(), sharedTopicId, "PENDING",
                null, null, null, null, null);
        registration(
                studentOpen, alternativeGroupOfThree.id(), sharedTopicId, "PENDING",
                null, null, null, null, null);

        CreatedRow closedGroup = group(registrationClosed.id(), students[0]);
        if (closedGroup.created()) {
            membership(closedGroup.id(), registrationClosed.id(), students[1], 2);
            membership(closedGroup.id(), registrationClosed.id(), students[2], 3);
        }
        registration(
                registrationClosed, closedGroup.id(), closedTopicId, "ACCEPTED",
                deanId,
                registrationClosed.studentStart().plus(1, ChronoUnit.DAYS),
                null,
                null,
                null);

        log.info(
                "Local demo data is ready: dean.demo, lecturer1.demo..lecturer3.demo, "
                        + "student1.demo..student7.demo. Disable DEMO_SEED_ENABLED after seeding.");
    }

    private long faculty(String name) {
        Long existing = optionalId(
                "SELECT id FROM faculties WHERE name = ? ORDER BY id LIMIT 1", name);
        return existing != null
                ? existing
                : insert("INSERT INTO faculties(name) VALUES (?)", name);
    }

    private long department(String name, long facultyId) {
        Long existing = optionalId(
                "SELECT id FROM departments WHERE faculty_id = ? AND name = ? ORDER BY id LIMIT 1",
                facultyId, name);
        return existing != null
                ? existing
                : insert(
                        "INSERT INTO departments(name, faculty_id) VALUES (?, ?)",
                        name, facultyId);
    }

    private long account(
            String username,
            String fullName,
            long departmentId,
            String passwordHash,
            String... roles) {
        Long existing = optionalId(
                "SELECT id FROM user_accounts WHERE username = ?", username);
        long accountId = existing != null
                ? existing
                : insert(
                        "INSERT INTO user_accounts(full_name, enabled, username, password_hash, "
                                + "department_id) VALUES (?, TRUE, ?, ?, ?)",
                        fullName, username, passwordHash, departmentId);
        for (String role : roles) {
            if (!exists(
                    "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_code = ?",
                    accountId, role)) {
                jdbc.update(
                        "INSERT INTO user_roles(user_id, role_code) VALUES (?, ?)",
                        accountId, role);
            }
        }
        return accountId;
    }

    private PeriodWindow period(
            long facultyId,
            String name,
            String topicType,
            LocalDate lecturerStart,
            LocalDate lecturerEnd,
            LocalDate studentStart,
            LocalDate studentEnd,
            LocalDate reviewerScoreDeadline,
            LocalDate councilReportDate) {
        Long existing = optionalId(
                "SELECT id FROM registration_periods "
                        + "WHERE faculty_id = ? AND name = ? ORDER BY id LIMIT 1",
                facultyId, name);
        long id = existing != null
                ? existing
                : insert(
                        "INSERT INTO registration_periods("
                                + "faculty_id, name, topic_type, lecturer_start, lecturer_end, "
                                + "student_start, student_end, reviewer_score_deadline, "
                                + "council_report_date, version) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                        facultyId,
                        name,
                        topicType,
                        Timestamp.from(projectTime.toStartOfDay(lecturerStart)),
                        Timestamp.from(projectTime.toEndOfDay(lecturerEnd)),
                        Timestamp.from(projectTime.toStartOfDay(studentStart)),
                        Timestamp.from(projectTime.toEndOfDay(studentEnd)),
                        reviewerScoreDeadline,
                        councilReportDate);
        return requiredPeriodWindow(id);
    }

    private PeriodWindow requiredPeriodWindow(long periodId) {
        return jdbc.queryForObject(
                "SELECT id, student_start, student_end FROM registration_periods WHERE id = ?",
                (resultSet, rowNumber) -> new PeriodWindow(
                        resultSet.getLong("id"),
                        resultSet.getTimestamp("student_start").toInstant(),
                        resultSet.getTimestamp("student_end").toInstant()),
                periodId);
    }

    private long topic(
            long periodId,
            long departmentId,
            long authorId,
            String title,
            String status) {
        Long existing = optionalId(
                "SELECT id FROM topics WHERE period_id = ? AND title = ? ORDER BY id LIMIT 1",
                periodId, title);
        if (existing != null) {
            return existing;
        }
        return insert(
                "INSERT INTO topics("
                        + "period_id, department_id, author_id, title, description, objectives, "
                        + "student_requirements, expected_results, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                periodId,
                departmentId,
                authorId,
                title,
                "Mô tả tổng hợp phục vụ kiểm tra giao diện và luồng nghiệp vụ local.",
                "Minh họa đầy đủ quy trình quản lý đề tài của hệ thống.",
                "Sinh viên có kiến thức nền phù hợp với nội dung đề tài.",
                "Sản phẩm demo và báo cáo mô tả kết quả thực hiện.",
                status);
    }

    private void supervisor(long topicId, long lecturerId, int slot) {
        if (!exists(
                "SELECT COUNT(*) FROM topic_supervisors "
                        + "WHERE topic_id = ? AND lecturer_id = ?",
                topicId, lecturerId)
                && !exists(
                        "SELECT COUNT(*) FROM topic_supervisors WHERE topic_id = ? AND slot = ?",
                        topicId, slot)) {
            jdbc.update(
                    "INSERT INTO topic_supervisors(topic_id, lecturer_id, slot) VALUES (?, ?, ?)",
                    topicId, lecturerId, slot);
        }
    }

    private CreatedRow group(long periodId, long leaderId) {
        Long existing = optionalId(
                "SELECT id FROM student_groups "
                        + "WHERE period_id = ? AND leader_student_id = ? ORDER BY id LIMIT 1",
                periodId, leaderId);
        if (existing != null) {
            return new CreatedRow(existing, false);
        }
        return new CreatedRow(
                insert(
                        "INSERT INTO student_groups(period_id, leader_student_id, version) "
                                + "VALUES (?, ?, 0)",
                        periodId, leaderId),
                true);
    }

    private void membership(long groupId, long periodId, long studentId, int slot) {
        if (!exists(
                "SELECT COUNT(*) FROM group_memberships "
                        + "WHERE period_id = ? AND student_id = ?",
                periodId, studentId)) {
            jdbc.update(
                    "INSERT INTO group_memberships(group_id, period_id, student_id, slot) "
                            + "VALUES (?, ?, ?, ?)",
                    groupId, periodId, studentId, slot);
        }
    }

    private void registration(
            PeriodWindow period,
            long groupId,
            long topicId,
            String status,
            Long decisionActorId,
            Instant decidedAt,
            Instant withdrawnAt,
            String rejectionKind,
            String rejectionReason) {
        if (exists(
                "SELECT COUNT(*) FROM registrations "
                        + "WHERE group_id = ? AND topic_id = ?",
                groupId, topicId)) {
            return;
        }
        Instant submittedAt = period.studentStart().plus(1, ChronoUnit.HOURS);
        jdbc.update(
                "INSERT INTO registrations("
                        + "period_id, group_id, topic_id, status, submitted_at, decided_at, "
                        + "withdrawn_at, decision_actor_id, rejection_kind, rejection_reason, "
                        + "version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                period.id(),
                groupId,
                topicId,
                status,
                Timestamp.from(submittedAt),
                timestamp(decidedAt),
                timestamp(withdrawnAt),
                decisionActorId,
                rejectionKind,
                rejectionReason);
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private boolean exists(String sql, Object... arguments) {
        Integer count = jdbc.queryForObject(sql, Integer.class, arguments);
        return count != null && count > 0;
    }

    private Long optionalId(String sql, Object... arguments) {
        List<Long> ids = jdbc.query(sql, (resultSet, rowNumber) -> resultSet.getLong(1), arguments);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private long insert(String sql, Object... arguments) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < arguments.length; index++) {
                statement.setObject(index + 1, arguments[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Could not read generated demo fixture id");
        }
        return key.longValue();
    }

    private record PeriodWindow(long id, Instant studentStart, Instant studentEnd) {
    }

    private record CreatedRow(long id, boolean created) {
    }
}

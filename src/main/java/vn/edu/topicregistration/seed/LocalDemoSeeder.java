package vn.edu.topicregistration.seed;

import vn.edu.topicregistration.util.ProjectTime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import vn.edu.topicregistration.repository.ReportFileRepository;
import vn.edu.topicregistration.repository.ReportFileRepository.StoredReportFile;


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
    private static final byte[] DEMO_REPORT_PDF = createDemoReportPdf();

    private static final String FACULTY_NAME = "[DEMO] Khoa Công nghệ Thông tin";
    private static final String SOFTWARE_DEPARTMENT = "[DEMO] Công nghệ phần mềm";
    private static final String INFORMATION_SYSTEMS_DEPARTMENT = "[DEMO] Hệ thống thông tin";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final ProjectTime projectTime;
    private final DemoSeedProperties properties;
    private final ReportFileRepository reportFiles;

    @Autowired
    public LocalDemoSeeder(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            ProjectTime projectTime,
            DemoSeedProperties properties,
            ReportFileRepository reportFiles) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.projectTime = projectTime;
        this.properties = properties;
        this.reportFiles = reportFiles;
    }

    /**
     * Keeps older direct-construction migration checks source-compatible. Review fixtures need
     * the five-argument constructor so their PDF metadata can never point to a missing file.
     */
    public LocalDemoSeeder(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            ProjectTime projectTime,
            DemoSeedProperties properties) {
        this(jdbc, passwordEncoder, projectTime, properties, null);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String password = properties.password();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "DEMO_SEED_PASSWORD is required when DEMO_SEED_ENABLED=true");
        }

        String passwordHash = passwordEncoder.encode(password);
        long facultyId = faculty(FACULTY_NAME);
        long softwareDepartmentId = department(SOFTWARE_DEPARTMENT, facultyId);
        long informationSystemsDepartmentId = department(
                INFORMATION_SYSTEMS_DEPARTMENT, facultyId);

        account(
                "admin.demo", "Quản trị viên Demo", null, passwordHash,
                "ADMIN");
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

        if (reviewSchemaAvailable()) {
            if (reportFiles == null) {
                log.info(
                        "Review schema is available, but no report storage was supplied; "
                                + "skipping review demo fixtures for this direct seeder run.");
            } else {
                seedReviewFixtures(
                        passwordHash,
                        facultyId,
                        softwareDepartmentId,
                        deanId,
                        lecturer1Id,
                        lecturer2Id,
                        students,
                        today);
            }
        }

        log.info(
                "Local demo data is ready. Existing rows and workflow states were preserved; "
                        + "disable DEMO_SEED_ENABLED after seeding.");
    }

    private void seedReviewFixtures(
            String passwordHash,
            long facultyId,
            long departmentId,
            long deanId,
            long chairId,
            long supervisorId,
            long[] students,
            LocalDate today) {
        long secretaryId = account(
                "lecturer4.demo", "Giảng viên Demo 4", departmentId, passwordHash,
                "LECTURER");
        long memberId = account(
                "lecturer5.demo", "Giảng viên Demo 5", departmentId, passwordHash,
                "LECTURER");

        PeriodWindow activePeriod = period(
                facultyId,
                "[DEMO] Khóa luận tốt nghiệp - Đang chấm",
                "KLTN",
                today.minusDays(90),
                today.minusDays(80),
                today.minusDays(75),
                today.minusDays(65),
                today.plusDays(7),
                today.plusDays(10));
        long activeTopicOne = topic(
                activePeriod.id(), departmentId, supervisorId,
                "[DEMO] Cổng quản lý chấm khóa luận", "PUBLISHED");
        supervisor(activeTopicOne, supervisorId, 1);
        long activeTopicTwo = topic(
                activePeriod.id(), departmentId, supervisorId,
                "[DEMO] Theo dõi tiến độ khóa luận", "PUBLISHED");
        supervisor(activeTopicTwo, supervisorId, 1);

        CreatedRow activeGroupOne = group(activePeriod.id(), students[0]);
        if (activeGroupOne.created()) {
            membership(activeGroupOne.id(), activePeriod.id(), students[1], 2);
            membership(activeGroupOne.id(), activePeriod.id(), students[2], 3);
        }
        CreatedRow activeGroupTwo = group(activePeriod.id(), students[3]);
        if (activeGroupTwo.created()) {
            membership(activeGroupTwo.id(), activePeriod.id(), students[4], 2);
            membership(activeGroupTwo.id(), activePeriod.id(), students[5], 3);
        }
        long activeRegistrationOne = registration(
                activePeriod, activeGroupOne.id(), activeTopicOne, "ACCEPTED", deanId,
                activePeriod.studentStart().plus(1, ChronoUnit.DAYS), null, null, null);
        long activeRegistrationTwo = registration(
                activePeriod, activeGroupTwo.id(), activeTopicTwo, "ACCEPTED", deanId,
                activePeriod.studentStart().plus(1, ChronoUnit.DAYS), null, null, null);

        CreatedRow activeCouncil = council(
                activePeriod.id(), departmentId,
                "[DEMO] Hội đồng KLTN - Đang chấm",
                chairId, secretaryId, "ACTIVE", today.minusDays(14));
        if (activeCouncil.created()) {
            report(activeRegistrationOne, students[0], today.minusDays(12),
                    "bao-cao-khoa-luan-nhom-1.pdf");
            report(activeRegistrationTwo, students[3], today.minusDays(12),
                    "bao-cao-khoa-luan-nhom-2.pdf");
            councilMember(activeCouncil.id(), memberId, 3);
            councilTopic(
                    activeCouncil.id(), activeRegistrationOne, activePeriod.id(),
                    today.minusDays(1), "Phòng A4-101");
            councilTopic(
                    activeCouncil.id(), activeRegistrationTwo, activePeriod.id(),
                    today.minusDays(1), "Phòng A4-101");
            long componentId = scoreComponent(activePeriod.id());
            Instant scoredAt = projectTime.now().minus(1, ChronoUnit.DAYS);
            evaluation(activeCouncil.id(), activeRegistrationOne, activePeriod.id(),
                    chairId, componentId, "8.00", "Nội dung đầy đủ, trình bày rõ ràng.", scoredAt);
            evaluation(activeCouncil.id(), activeRegistrationOne, activePeriod.id(),
                    secretaryId, componentId, "8.50", "Giải pháp phù hợp với bài toán.", scoredAt);
            evaluation(activeCouncil.id(), activeRegistrationOne, activePeriod.id(),
                    memberId, componentId, "9.00", "Sản phẩm hoạt động ổn định.", scoredAt);
            evaluation(activeCouncil.id(), activeRegistrationTwo, activePeriod.id(),
                    chairId, componentId, "7.75", "Cần làm rõ thêm phần kiểm chứng.", scoredAt);
            evaluation(activeCouncil.id(), activeRegistrationTwo, activePeriod.id(),
                    secretaryId, componentId, "8.25", "Kiến trúc và giao diện hợp lý.", scoredAt);
            evaluation(activeCouncil.id(), activeRegistrationTwo, activePeriod.id(),
                    memberId, componentId, "8.00", "Đáp ứng các yêu cầu chính.", scoredAt);
        }

        PeriodWindow publishedPeriod = period(
                facultyId,
                "[DEMO] Khóa luận tốt nghiệp - Đã công bố",
                "KLTN",
                today.minusDays(160),
                today.minusDays(150),
                today.minusDays(145),
                today.minusDays(135),
                today.minusDays(100),
                today.minusDays(90));
        long publishedTopic = topic(
                publishedPeriod.id(), departmentId, supervisorId,
                "[DEMO] Hệ thống hỗ trợ quản lý đề tài", "PUBLISHED");
        supervisor(publishedTopic, supervisorId, 1);
        CreatedRow publishedGroup = group(publishedPeriod.id(), students[0]);
        if (publishedGroup.created()) {
            membership(publishedGroup.id(), publishedPeriod.id(), students[1], 2);
            membership(publishedGroup.id(), publishedPeriod.id(), students[2], 3);
        }
        long publishedRegistration = registration(
                publishedPeriod, publishedGroup.id(), publishedTopic, "ACCEPTED", deanId,
                publishedPeriod.studentStart().plus(1, ChronoUnit.DAYS), null, null, null);
        CreatedRow publishedCouncil = council(
                publishedPeriod.id(), departmentId,
                "[DEMO] Hội đồng KLTN - Đã công bố",
                chairId, secretaryId, "PUBLISHED", today.minusDays(110));
        if (publishedCouncil.created()) {
            report(publishedRegistration, students[0], today.minusDays(108),
                    "bao-cao-khoa-luan-da-cong-bo.pdf");
            councilMember(publishedCouncil.id(), memberId, 3);
            councilTopic(
                    publishedCouncil.id(), publishedRegistration, publishedPeriod.id(),
                    today.minusDays(93), "Phòng A4-102");
            long componentId = scoreComponent(publishedPeriod.id());
            Instant scoredAt = projectTime.now().minus(92, ChronoUnit.DAYS);
            evaluation(publishedCouncil.id(), publishedRegistration, publishedPeriod.id(),
                    chairId, componentId, "9.00", "Báo cáo có chất lượng tốt.", scoredAt);
            evaluation(publishedCouncil.id(), publishedRegistration, publishedPeriod.id(),
                    secretaryId, componentId, "8.50", "Sản phẩm đáp ứng mục tiêu.", scoredAt);
            evaluation(publishedCouncil.id(), publishedRegistration, publishedPeriod.id(),
                    memberId, componentId, "8.75", "Phần trình bày và phản biện tốt.", scoredAt);
            finalResult(
                    publishedRegistration,
                    publishedCouncil.id(),
                    chairId,
                    deanId,
                    projectTime.now().minus(91, ChronoUnit.DAYS),
                    projectTime.now().minus(90, ChronoUnit.DAYS),
                    new BigDecimal("8.75"));
        }

        log.info(
                "Review demo fixtures are ready: ACTIVE board {}, PUBLISHED board {}, "
                        + "lecturer4.demo and lecturer5.demo.",
                activeCouncil.id(), publishedCouncil.id());
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
            Long departmentId,
            String passwordHash,
            String... roles) {
        Long existing = optionalId(
                "SELECT id FROM user_accounts WHERE username = ?", username);
        if (existing != null) {
            return existing;
        }
        long accountId = insert(
                "INSERT INTO user_accounts(full_name, enabled, username, password_hash, "
                        + "department_id) VALUES (?, TRUE, ?, ?, ?)",
                fullName, username, passwordHash, departmentId);
        for (String role : roles) {
            jdbc.update(
                    "INSERT INTO user_roles(user_id, role_code) VALUES (?, ?)",
                    accountId, role);
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

    private long registration(
            PeriodWindow period,
            long groupId,
            long topicId,
            String status,
            Long decisionActorId,
            Instant decidedAt,
            Instant withdrawnAt,
            String rejectionKind,
            String rejectionReason) {
        Long existing = optionalId(
                "SELECT id FROM registrations WHERE group_id = ? AND topic_id = ?",
                groupId, topicId);
        if (existing != null) {
            return existing;
        }
        Instant submittedAt = period.studentStart().plus(1, ChronoUnit.HOURS);
        return insert(
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

    private CreatedRow council(
            long periodId,
            long departmentId,
            String name,
            long chairId,
            long secretaryId,
            String status,
            LocalDate createdDate) {
        Long existing = optionalId(
                "SELECT id FROM councils WHERE period_id = ? AND name = ? ORDER BY id LIMIT 1",
                periodId, name);
        if (existing != null) {
            return new CreatedRow(existing, false);
        }
        return new CreatedRow(insert(
                "INSERT INTO councils("
                        + "period_id, department_id, name, chair_id, secretary_id, status, "
                        + "created_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                periodId,
                departmentId,
                name,
                chairId,
                secretaryId,
                status,
                Timestamp.from(projectTime.toStartOfDay(createdDate))), true);
    }

    private void councilMember(long councilId, long lecturerId, int slot) {
        if (!exists(
                "SELECT COUNT(*) FROM council_members WHERE council_id = ? AND lecturer_id = ?",
                councilId, lecturerId)) {
            jdbc.update(
                    "INSERT INTO council_members(council_id, lecturer_id, slot) VALUES (?, ?, ?)",
                    councilId, lecturerId, slot);
        }
    }

    private void councilTopic(
            long councilId,
            long registrationId,
            long periodId,
            LocalDate scheduledDate,
            String location) {
        if (!exists(
                "SELECT COUNT(*) FROM council_topics "
                        + "WHERE council_id = ? AND registration_id = ?",
                councilId, registrationId)) {
            jdbc.update(
                    "INSERT INTO council_topics("
                            + "council_id, registration_id, period_id, registration_status, "
                            + "scheduled_at, location) VALUES (?, ?, ?, 'ACCEPTED', ?, ?)",
                    councilId,
                    registrationId,
                    periodId,
                    Timestamp.from(projectTime.toStartOfDay(scheduledDate).plus(8, ChronoUnit.HOURS)),
                    location);
        }
    }

    private long scoreComponent(long periodId) {
        Long existing = optionalId(
                "SELECT id FROM score_components WHERE period_id = ? AND name = ?",
                periodId, "Điểm hội đồng");
        return existing != null
                ? existing
                : insert(
                        "INSERT INTO score_components("
                                + "period_id, name, minimum_score, maximum_score) "
                                + "VALUES (?, 'Điểm hội đồng', 0.00, 10.00)",
                        periodId);
    }

    private void evaluation(
            long councilId,
            long registrationId,
            long periodId,
            long evaluatorId,
            long componentId,
            String score,
            String feedback,
            Instant submittedAt) {
        if (!exists(
                "SELECT COUNT(*) FROM evaluations "
                        + "WHERE registration_id = ? AND evaluator_id = ? AND component_id = ?",
                registrationId, evaluatorId, componentId)) {
            jdbc.update(
                    "INSERT INTO evaluations("
                            + "registration_id, period_id, registration_status, evaluator_id, "
                            + "component_id, council_id, score, feedback, status, evaluated_at, "
                            + "submitted_at, version) "
                            + "VALUES (?, ?, 'ACCEPTED', ?, ?, ?, ?, ?, 'SUBMITTED', ?, ?, 0)",
                    registrationId,
                    periodId,
                    evaluatorId,
                    componentId,
                    councilId,
                    new BigDecimal(score),
                    feedback,
                    Timestamp.from(submittedAt),
                    Timestamp.from(submittedAt));
        }
    }

    private void finalResult(
            long registrationId,
            long councilId,
            long consolidatedBy,
            long publishedBy,
            Instant completedAt,
            Instant publishedAt,
            BigDecimal finalScore) {
        if (!exists(
                "SELECT COUNT(*) FROM final_results WHERE registration_id = ?",
                registrationId)) {
            jdbc.update(
                    "INSERT INTO final_results("
                            + "registration_id, registration_status, council_id, consolidated_by, "
                            + "consolidated_at, evaluation_summary, final_score, "
                            + "review_completed_at, grading_completed_at, published_by, "
                            + "published_at, status, version) "
                            + "VALUES (?, 'ACCEPTED', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', 0)",
                    registrationId,
                    councilId,
                    consolidatedBy,
                    Timestamp.from(completedAt),
                    "3/3 đánh giá đã gửi; điểm cuối là trung bình cộng.",
                    finalScore,
                    Timestamp.from(completedAt),
                    Timestamp.from(completedAt),
                    publishedBy,
                    Timestamp.from(publishedAt));
        }
    }

    private void report(
            long registrationId,
            long submittedBy,
            LocalDate submittedDate,
            String fileName) {
        if (exists(
                "SELECT COUNT(*) FROM project_reports "
                        + "WHERE registration_id = ? AND replaced_at IS NULL",
                registrationId)) {
            return;
        }

        StoredReportFile stored;
        try {
            stored = reportFiles.store(
                    new ByteArrayInputStream(DEMO_REPORT_PDF), DEMO_REPORT_PDF.length);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not store the local demo report", exception);
        }
        deleteReportOnRollback(stored.storageKey());
        try {
            jdbc.update(
                    "INSERT INTO project_reports("
                            + "registration_id, registration_status, submitted_by, submitted_at, "
                            + "file_name, storage_key, content_type, size_bytes, version) "
                            + "VALUES (?, 'ACCEPTED', ?, ?, ?, ?, 'application/pdf', ?, 0)",
                    registrationId,
                    submittedBy,
                    Timestamp.from(projectTime.toStartOfDay(submittedDate)),
                    fileName,
                    stored.storageKey(),
                    stored.sizeBytes());
        } catch (RuntimeException exception) {
            reportFiles.delete(stored.storageKey());
            throw exception;
        }
    }

    private void deleteReportOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            reportFiles.delete(storageKey);
                        }
                    }
                });
    }

    private boolean reviewSchemaAvailable() {
        return exists(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'councils' "
                        + "AND column_name = 'status'");
    }

    private static byte[] createDemoReportPdf() {
        String content = "BT /F1 18 Tf 72 760 Td ([DEMO] Student topic report) Tj ET\n";
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
                "<< /Length " + content.getBytes(StandardCharsets.US_ASCII).length
                        + " >>\nstream\n" + content + "endstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeAscii(output, "%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            writeAscii(output, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n");
        }
        int xrefOffset = output.size();
        writeAscii(output, "xref\n0 " + (objects.size() + 1) + "\n");
        writeAscii(output, "0000000000 65535 f \n");
        for (Integer offset : offsets) {
            writeAscii(output, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        }
        writeAscii(output,
                "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n"
                        + "startxref\n" + xrefOffset + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
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

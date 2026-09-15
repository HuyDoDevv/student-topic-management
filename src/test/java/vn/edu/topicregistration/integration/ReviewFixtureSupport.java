package vn.edu.topicregistration.integration;

import java.nio.file.Path;
import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import vn.edu.topicregistration.config.ReportStorageProperties;
import vn.edu.topicregistration.repository.LocalReportFileRepository;
import vn.edu.topicregistration.seed.DemoSeedProperties;
import vn.edu.topicregistration.seed.LocalDemoSeeder;
import vn.edu.topicregistration.util.ProjectTime;

/** Shared deterministic fixture for the T008 integration, concurrency and browser journeys. */
public final class ReviewFixtureSupport {
    public static final String ACTIVE_COUNCIL = "[DEMO] Hội đồng KLTN - Đang chấm";
    public static final String PUBLISHED_COUNCIL = "[DEMO] Hội đồng KLTN - Đã công bố";
    public static final String ACTIVE_TOPIC_ONE = "[DEMO] Cổng quản lý chấm khóa luận";
    public static final String ACTIVE_TOPIC_TWO = "[DEMO] Theo dõi tiến độ khóa luận";
    public static final String PUBLISHED_TOPIC = "[DEMO] Hệ thống hỗ trợ quản lý đề tài";
    public static final String UPLOAD_TOPIC = "[DEMO] Nền tảng quản lý khóa luận";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Path reportRoot;

    public ReviewFixtureSupport(DataSource dataSource, Clock clock, Path reportRoot) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.clock = clock;
        this.reportRoot = reportRoot;
    }

    public ReviewFixture seed(String password) {
        LocalDemoSeeder seeder = new LocalDemoSeeder(
                jdbc,
                new BCryptPasswordEncoder(4),
                new ProjectTime(clock),
                new DemoSeedProperties(true, password),
                new LocalReportFileRepository(new ReportStorageProperties(reportRoot)));
        seeder.run(new DefaultApplicationArguments(new String[0]));
        return currentFixture();
    }

    public ReviewFixture currentFixture() {
        return new ReviewFixture(
                account("dean.demo"),
                account("lecturer1.demo"),
                account("lecturer4.demo"),
                account("lecturer5.demo"),
                account("student1.demo"),
                account("student4.demo"),
                council(ACTIVE_COUNCIL),
                council(PUBLISHED_COUNCIL),
                registration(ACTIVE_TOPIC_ONE),
                registration(ACTIVE_TOPIC_TWO),
                registration(PUBLISHED_TOPIC),
                registration(UPLOAD_TOPIC));
    }

    private long account(String username) {
        return requiredId("SELECT id FROM user_accounts WHERE username = ?", username);
    }

    private long council(String name) {
        return requiredId("SELECT id FROM councils WHERE name = ?", name);
    }

    private long registration(String topicTitle) {
        return requiredId("""
                SELECT registration.id
                FROM registrations registration
                JOIN topics topic ON topic.id = registration.topic_id
                WHERE topic.title = ?
                """, topicTitle);
    }

    private long requiredId(String sql, Object... arguments) {
        Long id = jdbc.queryForObject(sql, Long.class, arguments);
        if (id == null) {
            throw new IllegalStateException("Review fixture row is missing");
        }
        return id;
    }

    public record ReviewFixture(
            long deanId,
            long chairId,
            long secretaryId,
            long memberId,
            long studentOneId,
            long studentFourId,
            long activeCouncilId,
            long publishedCouncilId,
            long activeRegistrationOneId,
            long activeRegistrationTwoId,
            long publishedRegistrationId,
            long uploadRegistrationId) {
    }
}

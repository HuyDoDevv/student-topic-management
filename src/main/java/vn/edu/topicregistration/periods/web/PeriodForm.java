package vn.edu.topicregistration.periods.web;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import vn.edu.topicregistration.periods.application.PeriodService.PeriodView;
import vn.edu.topicregistration.periods.domain.PeriodRules.PeriodConfiguration;
import vn.edu.topicregistration.periods.domain.PeriodRules.TopicType;
import vn.edu.topicregistration.shared.time.ProjectTime;

public class PeriodForm {
    @NotBlank(message = "Vui lòng nhập tên đợt.")
    @Size(max = 255, message = "Tên đợt không được dài quá 255 ký tự.")
    private String name;

    @NotNull(message = "Vui lòng chọn loại đề tài.")
    private TopicType topicType;

    @NotNull(message = "Vui lòng nhập thời điểm bắt đầu của giảng viên.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime lecturerStart;

    @NotNull(message = "Vui lòng nhập thời điểm kết thúc của giảng viên.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime lecturerEnd;

    @NotNull(message = "Vui lòng nhập thời điểm bắt đầu của sinh viên.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime studentStart;

    @NotNull(message = "Vui lòng nhập thời điểm kết thúc của sinh viên.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime studentEnd;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate reviewerScoreDeadline;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate councilReportDate;

    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public static PeriodForm from(PeriodView period, ProjectTime projectTime) {
        PeriodForm form = new PeriodForm();
        form.name = period.name();
        form.topicType = period.topicType();
        form.lecturerStart = projectTime.toBusinessTime(period.lecturerStart()).toLocalDateTime();
        form.lecturerEnd = projectTime.toBusinessTime(period.lecturerEnd()).toLocalDateTime();
        form.studentStart = projectTime.toBusinessTime(period.studentStart()).toLocalDateTime();
        form.studentEnd = projectTime.toBusinessTime(period.studentEnd()).toLocalDateTime();
        form.reviewerScoreDeadline = period.reviewerScoreDeadline();
        form.councilReportDate = period.councilReportDate();
        form.version = period.version();
        return form;
    }

    public PeriodConfiguration toConfiguration(ProjectTime projectTime) {
        return new PeriodConfiguration(
                name,
                topicType,
                toInstant(projectTime, lecturerStart),
                toInstant(projectTime, lecturerEnd),
                toInstant(projectTime, studentStart),
                toInstant(projectTime, studentEnd),
                reviewerScoreDeadline,
                councilReportDate);
    }

    private static java.time.Instant toInstant(ProjectTime projectTime, LocalDateTime value) {
        return value == null ? null : projectTime.toStoredInstant(value);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TopicType getTopicType() {
        return topicType;
    }

    public void setTopicType(TopicType topicType) {
        this.topicType = topicType;
    }

    public LocalDateTime getLecturerStart() {
        return lecturerStart;
    }

    public void setLecturerStart(LocalDateTime lecturerStart) {
        this.lecturerStart = lecturerStart;
    }

    public LocalDateTime getLecturerEnd() {
        return lecturerEnd;
    }

    public void setLecturerEnd(LocalDateTime lecturerEnd) {
        this.lecturerEnd = lecturerEnd;
    }

    public LocalDateTime getStudentStart() {
        return studentStart;
    }

    public void setStudentStart(LocalDateTime studentStart) {
        this.studentStart = studentStart;
    }

    public LocalDateTime getStudentEnd() {
        return studentEnd;
    }

    public void setStudentEnd(LocalDateTime studentEnd) {
        this.studentEnd = studentEnd;
    }

    public LocalDate getReviewerScoreDeadline() {
        return reviewerScoreDeadline;
    }

    public void setReviewerScoreDeadline(LocalDate reviewerScoreDeadline) {
        this.reviewerScoreDeadline = reviewerScoreDeadline;
    }

    public LocalDate getCouncilReportDate() {
        return councilReportDate;
    }

    public void setCouncilReportDate(LocalDate councilReportDate) {
        this.councilReportDate = councilReportDate;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

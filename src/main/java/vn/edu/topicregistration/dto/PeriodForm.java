package vn.edu.topicregistration.dto;

import vn.edu.topicregistration.util.ProjectTime;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import vn.edu.topicregistration.service.PeriodService.PeriodView;
import vn.edu.topicregistration.service.PeriodRules.PeriodConfiguration;
import vn.edu.topicregistration.service.PeriodRules.TopicType;

public class PeriodForm {
    @Positive(message = "Bộ môn không hợp lệ.")
    private Long departmentId;

    @NotBlank(message = "Vui lòng nhập tên đợt.")
    @Size(max = 255, message = "Tên đợt không được dài quá 255 ký tự.")
    private String name;

    @NotNull(message = "Vui lòng chọn loại đề tài.")
    private TopicType topicType;

    @NotNull(message = "Vui lòng nhập ngày bắt đầu của giảng viên.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate lecturerStart;

    @NotNull(message = "Vui lòng nhập ngày kết thúc của giảng viên.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate lecturerEnd;

    @NotNull(message = "Vui lòng nhập ngày bắt đầu của sinh viên.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate studentStart;

    @NotNull(message = "Vui lòng nhập ngày kết thúc của sinh viên.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate studentEnd;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate reviewerScoreDeadline;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate councilReportDate;

    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public static PeriodForm from(PeriodView period, ProjectTime projectTime) {
        PeriodForm form = new PeriodForm();
        form.departmentId = period.departmentId();
        form.name = period.name();
        form.topicType = period.topicType();
        form.lecturerStart = projectTime.toBusinessDate(period.lecturerStart());
        form.lecturerEnd = projectTime.toBusinessDate(period.lecturerEnd());
        form.studentStart = projectTime.toBusinessDate(period.studentStart());
        form.studentEnd = projectTime.toBusinessDate(period.studentEnd());
        form.reviewerScoreDeadline = period.reviewerScoreDeadline();
        form.councilReportDate = period.councilReportDate();
        form.version = period.version();
        return form;
    }

    public PeriodConfiguration toConfiguration(ProjectTime projectTime) {
        return new PeriodConfiguration(
                name,
                topicType,
                toStart(projectTime, lecturerStart),
                toEnd(projectTime, lecturerEnd),
                toStart(projectTime, studentStart),
                toEnd(projectTime, studentEnd),
                reviewerScoreDeadline,
                councilReportDate);
    }

    private static java.time.Instant toStart(ProjectTime projectTime, LocalDate value) {
        return value == null ? null : projectTime.toStartOfDay(value);
    }

    private static java.time.Instant toEnd(ProjectTime projectTime, LocalDate value) {
        return value == null ? null : projectTime.toEndOfDay(value);
    }

    public String getName() {
        return name;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
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

    public LocalDate getLecturerStart() {
        return lecturerStart;
    }

    public void setLecturerStart(LocalDate lecturerStart) {
        this.lecturerStart = lecturerStart;
    }

    public LocalDate getLecturerEnd() {
        return lecturerEnd;
    }

    public void setLecturerEnd(LocalDate lecturerEnd) {
        this.lecturerEnd = lecturerEnd;
    }

    public LocalDate getStudentStart() {
        return studentStart;
    }

    public void setStudentStart(LocalDate studentStart) {
        this.studentStart = studentStart;
    }

    public LocalDate getStudentEnd() {
        return studentEnd;
    }

    public void setStudentEnd(LocalDate studentEnd) {
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

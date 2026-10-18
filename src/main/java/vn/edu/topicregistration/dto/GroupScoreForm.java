package vn.edu.topicregistration.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import vn.edu.topicregistration.service.ScoringService.GroupScoreCommand;
import vn.edu.topicregistration.service.ScoringService.ScoreRowCommand;
import vn.edu.topicregistration.service.ScoringService.ScoreWorkspace;
import vn.edu.topicregistration.util.ScoreFormat;

public class GroupScoreForm {
    @NotNull(message = "Phiên bản nhiệm vụ không được để trống.")
    @PositiveOrZero(message = "Phiên bản nhiệm vụ không hợp lệ.")
    private Long councilVersion;

    @NotEmpty(message = "Phiếu chấm phải có ít nhất một sinh viên.")
    @Size(max = 3, message = "Phiếu chấm không được có quá ba sinh viên.")
    private List<@Valid Row> rows = new ArrayList<>();

    public static GroupScoreForm from(ScoreWorkspace workspace) {
        GroupScoreForm form = new GroupScoreForm();
        form.councilVersion = workspace.assignment().councilVersion();
        form.rows = workspace.students().stream().map(student -> {
            Row row = new Row();
            row.studentId = student.studentId();
            if (student.evaluation() != null) {
                row.score = ScoreFormat.normalize(student.evaluation().score());
                row.feedback = student.evaluation().feedback();
                row.version = student.evaluation().version();
            }
            return row;
        }).toList();
        return form;
    }

    public GroupScoreCommand toCommand() {
        return new GroupScoreCommand(
                councilVersion,
                rows.stream().map(row -> new ScoreRowCommand(
                        row.studentId, row.score, row.feedback, row.version)).toList());
    }

    public Long getCouncilVersion() {
        return councilVersion;
    }

    public void setCouncilVersion(Long councilVersion) {
        this.councilVersion = councilVersion;
    }

    public List<Row> getRows() {
        return rows;
    }

    public void setRows(List<Row> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    public static class Row {
        @NotNull(message = "Sinh viên không được để trống.")
        @Positive(message = "Sinh viên không hợp lệ.")
        private Long studentId;

        @NotNull(message = "Vui lòng nhập điểm.")
        @DecimalMin(value = "0.00", message = "Điểm không được nhỏ hơn 0.")
        @DecimalMax(value = "10.00", message = "Điểm không được lớn hơn 10.")
        @Digits(integer = 2, fraction = 2,
                message = "Điểm chỉ được có tối đa hai chữ số thập phân.")
        private BigDecimal score;

        private String feedback;

        @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
        private Long version;

        public boolean hasValidScoreIncrement() {
            return score == null
                    || score.remainder(new BigDecimal("0.25")).compareTo(BigDecimal.ZERO) == 0;
        }

        public Long getStudentId() {
            return studentId;
        }

        public void setStudentId(Long studentId) {
            this.studentId = studentId;
        }

        public BigDecimal getScore() {
            return score;
        }

        public void setScore(BigDecimal score) {
            this.score = score;
        }

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String feedback) {
            this.feedback = feedback;
        }

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }
    }
}

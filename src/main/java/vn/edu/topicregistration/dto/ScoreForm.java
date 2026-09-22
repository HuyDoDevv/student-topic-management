package vn.edu.topicregistration.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import vn.edu.topicregistration.service.ScoringService.EvaluationView;
import vn.edu.topicregistration.service.ScoringService.ScoreCommand;
import vn.edu.topicregistration.util.ScoreFormat;

public class ScoreForm {
    @NotNull(message = "Vui lòng nhập điểm.")
    @DecimalMin(value = "0.00", message = "Điểm không được nhỏ hơn 0.")
    @DecimalMax(value = "10.00", message = "Điểm không được lớn hơn 10.")
    @Digits(integer = 2, fraction = 2,
            message = "Điểm chỉ được có tối đa hai chữ số thập phân.")
    private BigDecimal score;

    private String feedback;

    @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.")
    private Long version;

    public static ScoreForm from(EvaluationView evaluation) {
        ScoreForm form = new ScoreForm();
        if (evaluation != null) {
            form.score = ScoreFormat.normalize(evaluation.score());
            form.feedback = evaluation.feedback();
            form.version = evaluation.version();
        }
        return form;
    }

    public ScoreCommand toCommand() {
        return new ScoreCommand(score, feedback, version);
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

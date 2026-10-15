package vn.edu.topicregistration.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ReportUpload;
import vn.edu.topicregistration.service.ReviewerAssignmentService.ScoreRow;
import vn.edu.topicregistration.service.ReviewerAssignmentService.SubmissionCommand;

public class ReviewerSubmissionForm {
    @NotNull @PositiveOrZero
    private Long version;
    private MultipartFile file;
    private List<StudentScore> scores = new ArrayList<>();

    public SubmissionCommand toCommand() {
        ReportUpload upload = file == null ? null : new ReportUpload(
                file.getOriginalFilename(), file.getContentType(), file.getSize(),
                file::getInputStream);
        return new SubmissionCommand(version, upload,
                scores == null ? null : scores.stream().map(StudentScore::toRow).toList());
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }
    public List<StudentScore> getScores() { return scores; }
    public void setScores(List<StudentScore> scores) { this.scores = scores; }

    public static class StudentScore {
        private Long studentId;
        private BigDecimal score;
        private String feedback;

        public ScoreRow toRow() { return new ScoreRow(studentId, score, feedback); }
        public Long getStudentId() { return studentId; }
        public void setStudentId(Long studentId) { this.studentId = studentId; }
        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
        public String getFeedback() { return feedback; }
        public void setFeedback(String feedback) { this.feedback = feedback; }
    }
}

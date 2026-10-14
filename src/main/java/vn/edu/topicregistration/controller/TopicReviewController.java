package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.TopicReviewService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import vn.edu.topicregistration.service.TopicReviewService.PublicationResult;
import vn.edu.topicregistration.service.TopicReviewService.ReviewResult;

@Controller
@Profile("!test")
public class TopicReviewController {
    private final TopicReviewService topics;

    public TopicReviewController(TopicReviewService topics) {
        this.topics = topics;
    }

    @PostMapping("/topics/{id}/approve")
    RedirectView approve(
            @PathVariable long id,
            @RequestParam(name = "version", required = false) Long version,
            @RequestParam(name = "departmentId", required = false) String departmentId) {
        rejectDepartmentParameter(departmentId);
        ReviewResult result = topics.approve(id, requiredVersion(version));
        return proposalReview(result.periodId());
    }

    @PostMapping("/topics/{id}/reject")
    RedirectView reject(
            @PathVariable long id,
            @RequestParam(name = "version", required = false) Long version,
            @RequestParam(name = "departmentId", required = false) String departmentId) {
        rejectDepartmentParameter(departmentId);
        ReviewResult result = topics.reject(id, requiredVersion(version));
        return proposalReview(result.periodId());
    }

    @PostMapping("/periods/{periodId}/topics/publish")
    RedirectView publish(@PathVariable long periodId,
            @RequestParam(name = "departmentId", required = false) String departmentId) {
        rejectDepartmentParameter(departmentId);
        PublicationResult result = topics.publishApproved(periodId);
        return proposalReview(result.periodId());
    }

    private long requiredVersion(Long version) {
        if (version == null || version < 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        return version;
    }

    private void rejectDepartmentParameter(String departmentId) {
        if (departmentId != null) {
            throw new ApplicationException(ApplicationErrorCode.BAD_REQUEST);
        }
    }

    private RedirectView proposalReview(long periodId) {
        return WebRedirects.seeOther("/periods/" + periodId + "/topics/review");
    }
}

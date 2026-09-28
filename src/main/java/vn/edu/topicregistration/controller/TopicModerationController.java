package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.TopicModerationService;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;


@Controller
@Profile("!test")
public class TopicModerationController {
    private final TopicModerationService moderation;

    public TopicModerationController(TopicModerationService moderation) {
        this.moderation = moderation;
    }

    @PostMapping("/admin/moderation/topics/{id}/hide")
    RedirectView hide(
            @PathVariable long id,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "version", required = false) Long version) {
        if (version == null || version < 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        moderation.hide(id, version, reason);
        return WebRedirects.seeOther("/dashboard");
    }
}

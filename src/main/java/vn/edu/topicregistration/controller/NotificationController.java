package vn.edu.topicregistration.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.NotificationItem;

@Controller
@Profile("!preview & !test")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    public String inbox(
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        if (page < 0) {
            throw new ApplicationException(ApplicationErrorCode.BAD_REQUEST);
        }

        Page<NotificationItem> notificationPage = notifications.inboxForCurrentActor(page);
        int displayedTotalPages = Math.max(1, notificationPage.getTotalPages());
        model.addAttribute("activeNav", "notifications");
        model.addAttribute("pageSection", "Thông báo");
        model.addAttribute("notifications", notificationPage.getContent());
        model.addAttribute("notificationPage", notificationPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("displayedPage", page + 1);
        model.addAttribute("displayedTotalPages", displayedTotalPages);
        return "notifications/list";
    }
}

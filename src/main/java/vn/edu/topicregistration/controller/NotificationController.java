package vn.edu.topicregistration.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.InboxCategory;
import vn.edu.topicregistration.service.NotificationService.InboxStatus;
import vn.edu.topicregistration.service.NotificationService.NotificationItem;

@Controller
@Profile("!test")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/notifications")
    public String inbox(
            @RequestParam(defaultValue = "ALL") InboxStatus status,
            @RequestParam(defaultValue = "ALL") InboxCategory category,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        if (page < 0) {
            throw new ApplicationException(ApplicationErrorCode.BAD_REQUEST);
        }

        Page<NotificationItem> notificationPage = notifications.inboxForCurrentActor(status, category, page);
        int displayedTotalPages = Math.max(1, notificationPage.getTotalPages());
        model.addAttribute("activeNav", "notifications");
        model.addAttribute("pageSection", "Thông báo");
        model.addAttribute("notifications", notificationPage.getContent());
        model.addAttribute("notificationPage", notificationPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("displayedPage", page + 1);
        model.addAttribute("displayedTotalPages", displayedTotalPages);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        return "notifications/list";
    }

    @PostMapping("/notifications/{id}/read")
    public RedirectView markRead(
            @PathVariable long id,
            RedirectAttributes redirectAttributes) {
        notifications.markAsReadForCurrentActor(id);
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã đánh dấu thông báo là đã đọc.");
        return WebRedirects.seeOther("/notifications");
    }

    @PostMapping("/notifications/read-all")
    public RedirectView markAllRead(RedirectAttributes redirectAttributes) {
        int updated = notifications.markAllAsReadForCurrentActor();
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã đánh dấu " + updated + " thông báo là đã đọc.");
        return WebRedirects.seeOther("/notifications");
    }

    @PostMapping("/notifications/{id}/open")
    public RedirectView open(@PathVariable long id) {
        return WebRedirects.seeOther(notifications.openForCurrentActor(id));
    }
}

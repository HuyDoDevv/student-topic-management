package vn.edu.topicregistration.controller;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import vn.edu.topicregistration.security.AccountPrincipal;
import vn.edu.topicregistration.service.NotificationService;
import vn.edu.topicregistration.service.NotificationService.HeaderView;

@ControllerAdvice
@Profile("!test")
public class NotificationModelAdvice {
    private final NotificationService notifications;

    public NotificationModelAdvice(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ModelAttribute
    void addNotificationHeader(Authentication authentication, Model model) {
        if (!hasAccountPrincipal(authentication)) {
            addEmptyHeader(model);
            return;
        }

        HeaderView header = notifications.headerForCurrentActor();
        long unreadCount = header.unreadCount();
        model.addAttribute("headerUnreadCount", unreadCount);
        model.addAttribute("headerUnreadBadge", unreadCount > 99 ? "99+" : Long.toString(unreadCount));
        model.addAttribute("headerNotifications", header.items());
        model.addAttribute("hasUnreadNotifications", unreadCount > 0);
        model.addAttribute("notificationAriaLabel", unreadCount > 0
                ? "Xem thông báo, " + unreadCount + " thông báo chưa đọc"
                : "Xem thông báo, không có thông báo chưa đọc");
    }

    private static boolean hasAccountPrincipal(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal;
    }

    private static void addEmptyHeader(Model model) {
        model.addAttribute("headerUnreadCount", 0L);
        model.addAttribute("headerUnreadBadge", "0");
        model.addAttribute("headerNotifications", List.of());
        model.addAttribute("hasUnreadNotifications", false);
        model.addAttribute("notificationAriaLabel", "Xem thông báo, không có thông báo chưa đọc");
    }
}

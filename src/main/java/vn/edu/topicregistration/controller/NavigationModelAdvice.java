package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.security.AccountPrincipal;
import vn.edu.topicregistration.entity.RoleCode;

import java.util.Locale;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;


@ControllerAdvice
public class NavigationModelAdvice {

    @ModelAttribute
    void addCurrentAccount(Authentication authentication, Model model) {
        boolean signedIn = authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AccountPrincipal;
        if (!signedIn) {
            model.addAttribute("canManagePeriods", false);
            model.addAttribute("canAuthorTopics", false);
            model.addAttribute("canManageGroups", false);
            model.addAttribute("canManageBoards", false);
            model.addAttribute("canScoreBoards", false);
            model.addAttribute("canViewFacultyResults", false);
            model.addAttribute("canViewStudentResults", false);
            model.addAttribute("canManageAccounts", false);
            model.addAttribute("canManageAnnouncements", false);
            return;
        }

        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        Set<RoleCode> roles = principal.roles();
        String displayName = principal.fullName() == null || principal.fullName().isBlank()
                ? principal.getUsername()
                : principal.fullName();

        model.addAttribute("currentUserName", displayName);
        model.addAttribute("currentUserInitials", initials(displayName));
        model.addAttribute("currentRoleLabel", roleLabel(roles));
        model.addAttribute("canManagePeriods", roles.contains(RoleCode.DEAN));
        model.addAttribute("canAuthorTopics",
                roles.contains(RoleCode.LECTURER) && !roles.contains(RoleCode.DEAN));
        model.addAttribute("canManageGroups", roles.contains(RoleCode.STUDENT));
        model.addAttribute("canManageBoards", roles.contains(RoleCode.DEAN));
        model.addAttribute("canScoreBoards", roles.contains(RoleCode.LECTURER));
        model.addAttribute("canViewFacultyResults", roles.contains(RoleCode.DEAN));
        model.addAttribute("canViewStudentResults", roles.contains(RoleCode.STUDENT));
        model.addAttribute("canManageAccounts", roles.contains(RoleCode.ADMIN));
        model.addAttribute("canManageAnnouncements",
                roles.contains(RoleCode.ADMIN) || roles.contains(RoleCode.DEAN));
    }

    private static String initials(String displayName) {
        String[] words = displayName.trim().split("\\s+");
        String first = words[0].substring(0, 1);
        String last = words.length == 1 ? "" : words[words.length - 1].substring(0, 1);
        return (first + last).toUpperCase(Locale.forLanguageTag("vi-VN"));
    }

    private static String roleLabel(Set<RoleCode> roles) {
        if (roles.contains(RoleCode.DEAN) && roles.contains(RoleCode.LECTURER)) {
            return "Trưởng khoa · Giảng viên";
        }
        if (roles.contains(RoleCode.DEAN)) {
            return "Trưởng khoa";
        }
        if (roles.contains(RoleCode.LECTURER)) {
            return "Giảng viên";
        }
        if (roles.contains(RoleCode.STUDENT)) {
            return "Sinh viên";
        }
        if (roles.contains(RoleCode.ADMIN)) {
            return "Quản trị hệ thống";
        }
        return "Người dùng";
    }
}

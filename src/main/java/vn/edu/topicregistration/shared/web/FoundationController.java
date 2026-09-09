package vn.edu.topicregistration.shared.web;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import vn.edu.topicregistration.shared.time.ProjectTime;

@Controller
public class FoundationController {
    private final ProjectTime projectTime;

    public FoundationController(ProjectTime projectTime) {
        this.projectTime = projectTime;
    }

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("today", projectTime.toBusinessTime(projectTime.now()).toLocalDate().format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))));
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("pageSection", "Không gian làm việc");
        return "dashboard/index";
    }
}

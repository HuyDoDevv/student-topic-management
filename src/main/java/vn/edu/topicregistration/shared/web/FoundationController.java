package vn.edu.topicregistration.shared.web;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FoundationController {
    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("today", LocalDate.now().format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))));
        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("pageSection", "Không gian làm việc");
        return "dashboard/index";
    }
}

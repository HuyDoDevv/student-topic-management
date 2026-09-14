package vn.edu.topicregistration.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("!preview")
public class LoginController {
    @GetMapping("/login")
    String login() {
        return "auth/login";
    }
}

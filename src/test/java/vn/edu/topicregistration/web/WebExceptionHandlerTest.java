package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.view.RedirectView;

import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.controller.WebRedirects;

class WebExceptionHandlerTest {
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new FailureController())
                .setControllerAdvice(new WebExceptionHandler())
                .build();
    }

    @Test
    void mapsMalformedForbiddenMissingConflictAndBusinessErrors() throws Exception {
        assertError("/failure/number/not-a-number", 400, "BAD_REQUEST");
        assertError("/failure/forbidden", 403, "ACCESS_DENIED");
        assertError("/failure/missing", 404, "RESOURCE_NOT_FOUND");
        assertError("/failure/conflict", 409, "CONFLICT");
        assertError("/failure/window", 422, "WINDOW_CLOSED");
    }

    @Test
    void unexpectedDetailsAreNotExposedToTheView() throws Exception {
        mvc.perform(get("/failure/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/error"))
                .andExpect(model().attribute("description", not(containsString("password_hash"))));
    }

    @Test
    void successfulMutationUsesSeeOtherAndRejectsExternalRedirects() throws Exception {
        mvc.perform(get("/failure/success"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/periods/1"));
        assertThrows(IllegalArgumentException.class,
                () -> WebRedirects.seeOther("https://malicious.example"));
    }

    private void assertError(String path, int status, String code) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is(status))
                .andExpect(view().name("error/error"))
                .andExpect(model().attribute("errorCode", code));
    }

    @Controller
    static class FailureController {
        @GetMapping("/failure/number/{value}")
        String number(@PathVariable long value) {
            return "unused";
        }

        @GetMapping("/failure/forbidden")
        String forbidden() {
            throw new AccessDeniedException("private details");
        }

        @GetMapping("/failure/missing")
        String missing() {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }

        @GetMapping("/failure/conflict")
        String conflict() {
            throw new OptimisticLockingFailureException("select password_hash from user_accounts");
        }

        @GetMapping("/failure/window")
        String window() {
            throw new ApplicationException(ApplicationErrorCode.WINDOW_CLOSED);
        }

        @GetMapping("/failure/unexpected")
        String unexpected() {
            throw new IllegalStateException("select password_hash from user_accounts");
        }

        @GetMapping("/failure/success")
        RedirectView success() {
            return WebRedirects.seeOther("/periods/1");
        }
    }
}

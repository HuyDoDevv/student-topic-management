package vn.edu.topicregistration.web;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.AccountSecurityController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.AccountSecurityService;

class AccountSecurityControllerTest {
    AccountSecurityService security;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        security = mock(AccountSecurityService.class);
        mvc = standaloneSetup(new AccountSecurityController(security))
                .setControllerAdvice(new WebExceptionHandler()).build();
    }

    @Test
    void rendersCurrentEmail() throws Exception {
        when(security.currentEmail()).thenReturn("owner@example.com");

        mvc.perform(get("/account/email"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/change-email"))
                .andExpect(model().attribute("currentEmail", "owner@example.com"))
                .andExpect(model().attributeExists("changeEmailForm"));
    }

    @Test
    void successfulEmailChangeWhitelistsEmailAndKeepsSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/account/email").session(session)
                        .param("email", "  owner@example.com  ")
                        .param("accountId", "999")
                        .param("username", "forged")
                        .param("roles", "ADMIN"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/account/email"))
                .andExpect(flash().attribute("successMessage", "Đã cập nhật email của bạn."));

        verify(security).changeEmail(argThat(command -> command.email().equals("owner@example.com")));
        assertDoesNotThrow(session::getCreationTime);
    }

    @Test
    void invalidEmailRendersInlineErrorWithoutMutation() throws Exception {
        mvc.perform(post("/account/email").param("email", "not-an-email"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("account/change-email"))
                .andExpect(model().attributeHasFieldErrors("changeEmailForm", "email"));

        verify(security).currentEmail();
        verify(security, never()).changeEmail(any());
    }

    @Test
    void duplicateEmailKeepsCurrentEmailAndShowsFieldError() throws Exception {
        when(security.currentEmail()).thenReturn("original@example.com");
        doThrow(new ApplicationException(ApplicationErrorCode.ACCOUNT_EMAIL_EXISTS))
                .when(security).changeEmail(any());

        mvc.perform(post("/account/email").param("email", "taken@example.com"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("account/change-email"))
                .andExpect(model().attribute("currentEmail", "original@example.com"))
                .andExpect(model().attributeHasFieldErrors("changeEmailForm", "email"));
    }

    @Test
    void invalidInputCannotBypassAuthorization() throws Exception {
        when(security.currentEmail()).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));

        mvc.perform(post("/account/email").param("email", "invalid"))
                .andExpect(status().isForbidden());
        verify(security, never()).changeEmail(any());
    }

    @Test
    void rendersPasswordChangeForm() throws Exception {
        mvc.perform(get("/account/password"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/change-password"))
                .andExpect(model().attributeExists("changePasswordForm"));
    }

    @Test
    void rejectsMismatchedOrInvalidNewPasswordWithoutWriting() throws Exception {
        mvc.perform(post("/account/password")
                        .param("currentPassword", "current-password")
                        .param("newPassword", "short")
                        .param("confirmPassword", "different"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("account/change-password"))
                .andExpect(model().attributeHasErrors("changePasswordForm"));

        verify(security, never()).changePassword(any());
    }

    @Test
    void mapsWrongCurrentPasswordToInlineError() throws Exception {
        doThrow(new ApplicationException(
                ApplicationErrorCode.ACCOUNT_CURRENT_PASSWORD_INVALID))
                .when(security).changePassword(any());

        mvc.perform(post("/account/password")
                        .param("currentPassword", "wrong-password")
                        .param("newPassword", "new-password-123")
                        .param("confirmPassword", "new-password-123"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(model().attributeHasFieldErrors(
                        "changePasswordForm", "currentPassword"));
    }

    @Test
    void successfulChangeInvalidatesSessionAndRedirectsToLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(post("/account/password")
                        .session(session)
                        .param("currentPassword", "current-password")
                        .param("newPassword", "new-password-123")
                        .param("confirmPassword", "new-password-123"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/login?passwordChanged"));

        verify(security).changePassword(argThat(command ->
                command.currentPassword().equals("current-password")
                        && command.newPassword().equals("new-password-123")));
        assertThrows(IllegalStateException.class, session::getCreationTime);
    }
}

package vn.edu.topicregistration.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.AdminAccountController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.AccountType;
import vn.edu.topicregistration.service.AdminAccountService.AccountWorkspace;

class AdminAccountControllerTest {
    AdminAccountService accounts;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = mock(AdminAccountService.class);
        when(accounts.workspace(any())).thenReturn(AccountWorkspace.empty());
        mvc = standaloneSetup(new AdminAccountController(accounts))
                .setControllerAdvice(new WebExceptionHandler())
                .build();
    }

    @Test
    void legacyWorkspaceRedirectsToStudentManagement() throws Exception {
        mvc.perform(get("/admin/accounts"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/students"));
    }

    @Test
    void rendersStudentManagementAndFilters() throws Exception {
        mvc.perform(get("/admin/accounts/students")
                        .param("query", "student")
                        .param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/accounts/list"))
                .andExpect(model().attributeExists("workspace"))
                .andExpect(model().attribute("managementTitle", "Quản lý sinh viên"))
                .andExpect(model().attribute("activeNav", "admin-students"));

        verify(accounts).workspace(argThat(
                filter -> filter.accountType() == AccountType.STUDENT));
    }

    @Test
    void rendersLecturerAndDeanManagementSeparately() throws Exception {
        mvc.perform(get("/admin/accounts/lecturers"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("managementTitle", "Quản lý giảng viên"))
                .andExpect(model().attribute("activeNav", "admin-lecturers"));

        mvc.perform(get("/admin/accounts/deans"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("managementTitle", "Quản lý trưởng khoa"))
                .andExpect(model().attribute("activeNav", "admin-deans"));

        verify(accounts).workspace(argThat(
                filter -> filter.accountType() == AccountType.LECTURER));
        verify(accounts).workspace(argThat(
                filter -> filter.accountType() == AccountType.DEAN));
    }

    @Test
    void validSingleCreateUsesWhitelistedFieldsAndRedirects() throws Exception {
        mvc.perform(post("/admin/accounts")
                        .param("accountType", "STUDENT")
                        .param("username", "student01")
                        .param("fullName", "Sinh viên Một")
                        .param("password", "temporary-password")
                        .param("departmentId", "7")
                        .param("roles", "ADMIN"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/students"));

        verify(accounts).create(any());
    }

    @Test
    void invalidSingleCreateRendersFieldErrorsWithoutCallingService() throws Exception {
        mvc.perform(post("/admin/accounts")
                        .param("accountType", "STUDENT")
                        .param("username", "")
                        .param("fullName", "")
                        .param("password", "short")
                        .param("departmentId", "0"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "adminAccountForm", "username", "fullName", "password", "departmentId"));

        verify(accounts, never()).create(any());
    }

    @Test
    void directRequestCannotCreateAnotherAdminAccount() throws Exception {
        mvc.perform(post("/admin/accounts")
                        .param("accountType", "ADMIN")
                        .param("username", "admin02")
                        .param("fullName", "Quản trị Hai")
                        .param("password", "temporary-password")
                        .param("departmentId", "7"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/form"))
                .andExpect(model().attributeHasErrors("adminAccountForm"));

        verify(accounts, never()).create(any());
    }

    @Test
    void bcryptByteLimitIsValidatedBeforeCallingService() throws Exception {
        mvc.perform(post("/admin/accounts")
                        .param("accountType", "STUDENT")
                        .param("username", "student02")
                        .param("fullName", "Sinh viên Hai")
                        .param("password", "😀".repeat(30))
                        .param("departmentId", "7"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/form"))
                .andExpect(model().attributeHasErrors("adminAccountForm"));

        verify(accounts, never()).create(any());
    }

    @Test
    void importEndpointIsNotMapped() throws Exception {
        mvc.perform(post("/admin/accounts/import"))
                .andExpect(status().isNotFound());
    }

    @Test
    void importTemplateEndpointIsNotMapped() throws Exception {
        mvc.perform(get("/admin/accounts/import-template.csv"))
                .andExpect(status().isNotFound());
    }
}

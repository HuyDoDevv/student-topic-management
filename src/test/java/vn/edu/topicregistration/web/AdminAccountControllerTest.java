package vn.edu.topicregistration.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.AdminAccountController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.AccountType;
import vn.edu.topicregistration.service.AdminAccountService.AccountWorkspace;
import vn.edu.topicregistration.service.AdminAccountService.ImportIssue;
import vn.edu.topicregistration.service.AdminAccountService.ImportResult;

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
    void invalidImportReturnsRowErrorsAndDoesNotRedirect() throws Exception {
        when(accounts.importCsv(any())).thenReturn(new ImportResult(
                0, List.of(new ImportIssue(3, "Loại tài khoản không hợp lệ."))));
        MockMultipartFile file = new MockMultipartFile(
                "file", "accounts.csv", "text/csv", "bad".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/admin/accounts/import").file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/form"))
                .andExpect(model().attributeExists("importIssues"));
    }

    @Test
    void templateDownloadUsesStableUtf8CsvHeader() throws Exception {
        when(accounts.csvTemplate()).thenReturn(
                "\ufeffaccount_type,username,full_name,password,department_id\r\n"
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/admin/accounts/import-template.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString(
                        "account_type,username,full_name,password,department_id")));
    }
}

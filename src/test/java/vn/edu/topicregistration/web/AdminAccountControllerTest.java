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
    void accountRootAndLegacyCreateRouteRedirectSafely() throws Exception {
        mvc.perform(get("/admin/accounts"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/students"));
        mvc.perform(get("/admin/accounts/new"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/students/new"));
    }

    @Test
    void rendersManagementPagesWithRoleSpecificCreateLinks() throws Exception {
        mvc.perform(get("/admin/accounts/students"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/accounts/list"))
                .andExpect(model().attribute("createPath", "/admin/accounts/students/new"));
        mvc.perform(get("/admin/accounts/lecturers"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("createPath", "/admin/accounts/lecturers/new"));
        mvc.perform(get("/admin/accounts/deans"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("createPath", "/admin/accounts/deans/new"));

        verify(accounts).workspace(argThat(
                filter -> filter.accountType() == AccountType.STUDENT));
        verify(accounts).workspace(argThat(
                filter -> filter.accountType() == AccountType.LECTURER));
        verify(accounts).workspace(argThat(
                filter -> filter.accountType() == AccountType.DEAN));
    }

    @Test
    void rendersThreeIndependentCreatePages() throws Exception {
        mvc.perform(get("/admin/accounts/students/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/accounts/student-form"))
                .andExpect(model().attributeExists("studentAccountForm", "facultyName"));
        mvc.perform(get("/admin/accounts/lecturers/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/accounts/lecturer-form"))
                .andExpect(model().attributeExists("lecturerAccountForm"));
        mvc.perform(get("/admin/accounts/deans/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/accounts/dean-form"))
                .andExpect(model().attributeExists("deanAccountForm"));
    }

    @Test
    void studentRouteDerivesIdentityAndIgnoresForgedFields() throws Exception {
        mvc.perform(post("/admin/accounts/students")
                        .param("studentCode", "23110001")
                        .param("fullName", "Sinh viên Một")
                        .param("className", "23110CL1")
                        .param("cohortCode", "K2023")
                        .param("password", "temporary-password")
                        .param("username", "forged")
                        .param("email", "forged@example.com")
                        .param("accountType", "ADMIN")
                        .param("facultyId", "999"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/students"));

        verify(accounts).createStudent(argThat(command ->
                command.studentCode().equals("23110001")
                        && command.fullName().equals("Sinh viên Một")
                        && command.className().equals("23110CL1")
                        && command.cohortCode().equals("K2023")));
    }

    @Test
    void lecturerAndDeanRoutesLockTheirAccountTypes() throws Exception {
        mvc.perform(post("/admin/accounts/lecturers")
                        .param("username", "lecturer01")
                        .param("fullName", "Giảng viên Một")
                        .param("password", "temporary-password")
                        .param("accountType", "ADMIN"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/lecturers"));
        mvc.perform(post("/admin/accounts/deans")
                        .param("username", "dean01")
                        .param("fullName", "Trưởng khoa Một")
                        .param("password", "temporary-password")
                        .param("accountType", "ADMIN"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/deans"));

        verify(accounts).createLecturer(argThat(command ->
                command.username().equals("lecturer01")));
        verify(accounts).createDean(argThat(command -> command.username().equals("dean01")));
    }

    @Test
    void invalidStudentFieldsRenderInlineErrorsWithoutWriting() throws Exception {
        mvc.perform(post("/admin/accounts/students")
                        .param("studentCode", "sdfsdf")
                        .param("fullName", "")
                        .param("className", "")
                        .param("cohortCode", "2023")
                        .param("password", "short"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/student-form"))
                .andExpect(model().attributeHasFieldErrors(
                        "studentAccountForm", "studentCode", "fullName", "className",
                        "cohortCode", "password"));

        verify(accounts, never()).createStudent(any());
    }

    @Test
    void studentCodeAndClassRejectNonNumericOrUnsupportedCharacters() throws Exception {
        mvc.perform(post("/admin/accounts/students")
                        .param("studentCode", "2311ABCD")
                        .param("fullName", "Sinh viên Một")
                        .param("className", "23110 CL1")
                        .param("cohortCode", "K2023")
                        .param("password", "temporary-password"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/student-form"))
                .andExpect(model().attributeHasFieldErrors(
                        "studentAccountForm", "studentCode", "className"));

        verify(accounts, never()).createStudent(any());
    }

    @Test
    void lowercaseStudentClassAndCohortPassWebValidation() throws Exception {
        mvc.perform(post("/admin/accounts/students")
                        .param("studentCode", "23110001")
                        .param("fullName", "Sinh viên Một")
                        .param("className", "23110cl1")
                        .param("cohortCode", "k2023")
                        .param("password", "temporary-password"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/students"));

        verify(accounts).createStudent(argThat(command ->
                command.studentCode().equals("23110001")
                        && command.className().equals("23110cl1")
                        && command.cohortCode().equals("k2023")));
    }

    @Test
    void lecturerAndDeanRejectInvalidIdentityAndPasswordFields() throws Exception {
        mvc.perform(post("/admin/accounts/lecturers")
                        .param("username", ".lecturer")
                        .param("fullName", "")
                        .param("password", "short"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/lecturer-form"))
                .andExpect(model().attributeHasFieldErrors(
                        "lecturerAccountForm", "username", "fullName", "password"));

        mvc.perform(post("/admin/accounts/deans")
                        .param("username", "dean-")
                        .param("fullName", "")
                        .param("password", "short"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/dean-form"))
                .andExpect(model().attributeHasFieldErrors(
                        "deanAccountForm", "username", "fullName", "password"));

        verify(accounts, never()).createLecturer(any());
        verify(accounts, never()).createDean(any());
    }

    @Test
    void bcryptByteLimitIsValidatedForEachForm() throws Exception {
        mvc.perform(post("/admin/accounts/deans")
                        .param("username", "dean02")
                        .param("fullName", "Trưởng khoa Hai")
                        .param("password", "😀".repeat(30)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(view().name("admin/accounts/dean-form"))
                .andExpect(model().attributeHasErrors("deanAccountForm"));

        verify(accounts, never()).createDean(any());
    }

    @Test
    void sharedPostAndRemovedImportRoutesAreNotMapped() throws Exception {
        MockMvc routeOnlyMvc = standaloneSetup(new AdminAccountController(accounts)).build();

        routeOnlyMvc.perform(post("/admin/accounts"))
                .andExpect(status().isMethodNotAllowed());
        routeOnlyMvc.perform(post("/admin/accounts/import"))
                .andExpect(status().isNotFound());
        routeOnlyMvc.perform(get("/admin/accounts/import-template.csv"))
                .andExpect(status().isNotFound());
    }
}

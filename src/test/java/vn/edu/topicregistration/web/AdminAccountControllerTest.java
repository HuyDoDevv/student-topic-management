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
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.AccountStatusResult;
import vn.edu.topicregistration.service.AdminAccountService.AccountType;
import vn.edu.topicregistration.service.AdminAccountService.AccountWorkspace;
import vn.edu.topicregistration.service.AdminAccountService.PasswordResetResult;
import vn.edu.topicregistration.service.AdminAccountService.PasswordResetTarget;

class AdminAccountControllerTest {
    AdminAccountService accounts;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = mock(AdminAccountService.class);
        when(accounts.workspace(any())).thenReturn(AccountWorkspace.empty());
        when(accounts.staffDepartmentOptions()).thenReturn(java.util.List.of());
        when(accounts.passwordResetTarget(7L)).thenReturn(new PasswordResetTarget(
                7L, "23110001", "Sinh viên Một", AccountType.STUDENT,
                "Sinh viên", true));
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
    void staffListsPassDepartmentFilterButStudentListDoesNot() throws Exception {
        mvc.perform(get("/admin/accounts/lecturers").param("departmentId", "11"))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/accounts/deans").param("departmentId", "11"))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/accounts/students").param("departmentId", "11"))
                .andExpect(status().isOk());

        verify(accounts).workspace(argThat(filter -> filter.accountType() == AccountType.LECTURER
                && Long.valueOf(11L).equals(filter.departmentId())));
        verify(accounts).workspace(argThat(filter -> filter.accountType() == AccountType.DEAN
                && Long.valueOf(11L).equals(filter.departmentId())));
        verify(accounts).workspace(argThat(filter -> filter.accountType() == AccountType.STUDENT
                && filter.departmentId() == null));
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
                        .param("departmentId", "11")
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
                        .param("departmentId", "11")
                        .param("accountType", "ADMIN"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/lecturers"));
        mvc.perform(post("/admin/accounts/deans")
                        .param("username", "dean01")
                        .param("fullName", "Trưởng khoa Một")
                        .param("password", "temporary-password")
                        .param("departmentId", "11")
                        .param("accountType", "ADMIN"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/deans"));

        verify(accounts).createLecturer(argThat(command ->
                command.username().equals("lecturer01") && command.departmentId() == 11L));
        verify(accounts).createDean(argThat(command ->
                command.username().equals("dean01") && command.departmentId() == 11L));
    }

    @Test
    void duplicateDeanShowsSpecificCreateErrorAndUnlockIsRejected() throws Exception {
        when(accounts.createDean(any())).thenThrow(
                new ApplicationException(ApplicationErrorCode.DEAN_ALREADY_ACTIVE));
        when(accounts.unlockAccount(9L)).thenThrow(
                new ApplicationException(ApplicationErrorCode.DEAN_ALREADY_ACTIVE));

        mvc.perform(post("/admin/accounts/deans")
                        .param("username", "dean02")
                        .param("fullName", "Trưởng khoa Hai")
                        .param("password", "temporary-password")
                        .param("departmentId", "11"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("admin/accounts/dean-form"))
                .andExpect(model().attributeHasErrors("deanAccountForm"));
        mvc.perform(post("/admin/accounts/9/unlock"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("error/error"))
                .andExpect(model().attribute("errorCode", "DEAN_ALREADY_ACTIVE"));
    }

    @Test
    void staffCreationRequiresDepartmentAndIgnoresForgedFaculty() throws Exception {
        mvc.perform(post("/admin/accounts/lecturers")
                        .param("username", "lecturer02")
                        .param("fullName", "Giảng viên Hai")
                        .param("password", "temporary-password"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(model().attributeHasFieldErrors("lecturerAccountForm", "departmentId"));
        verify(accounts, never()).createLecturer(any());

        mvc.perform(post("/admin/accounts/deans")
                        .param("username", "dean02")
                        .param("fullName", "Trưởng khoa Hai")
                        .param("password", "temporary-password")
                        .param("departmentId", "11")
                        .param("facultyId", "999"))
                .andExpect(status().isSeeOther());
        verify(accounts).createDean(argThat(command -> command.departmentId() == 11L));
    }

    @Test
    void departmentAssignmentUsesPathIdentityAndValidatesForm() throws Exception {
        when(accounts.staffDepartmentTarget(7L)).thenReturn(
                new AdminAccountService.StaffDepartmentTarget(
                        7L, "lecturer01", "Giảng viên Một", AccountType.LECTURER,
                        "Khoa CNTT", null));
        when(accounts.assignStaffDepartment(7L, 11L)).thenReturn(
                new AdminAccountService.StaffDepartmentTarget(
                        7L, "lecturer01", "Giảng viên Một", AccountType.LECTURER,
                        "Khoa CNTT", 11L));
        mvc.perform(get("/admin/accounts/7/department"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/accounts/department-form"));
        mvc.perform(post("/admin/accounts/7/department")
                        .param("departmentId", "11")
                        .param("accountId", "999")
                        .param("facultyId", "999"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/lecturers"));
        verify(accounts).assignStaffDepartment(7L, 11L);

        mvc.perform(post("/admin/accounts/7/department"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(model().attributeHasFieldErrors("staffDepartmentForm", "departmentId"));
    }

    @Test
    void invalidStudentFieldsRenderInlineErrorsWithoutWriting() throws Exception {
        mvc.perform(post("/admin/accounts/students")
                        .param("studentCode", "sdfsdf")
                        .param("fullName", "")
                        .param("className", "")
                        .param("cohortCode", "2023")
                        .param("password", "short"))
                .andExpect(status().isUnprocessableContent())
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
                .andExpect(status().isUnprocessableContent())
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
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("admin/accounts/lecturer-form"))
                .andExpect(model().attributeHasFieldErrors(
                        "lecturerAccountForm", "username", "fullName", "password"));

        mvc.perform(post("/admin/accounts/deans")
                        .param("username", "dean-")
                        .param("fullName", "")
                        .param("password", "short"))
                .andExpect(status().isUnprocessableContent())
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
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("admin/accounts/dean-form"))
                .andExpect(model().attributeHasErrors("deanAccountForm"));

        verify(accounts, never()).createDean(any());
    }

    @Test
    void rendersResetPasswordFormForManagedAccount() throws Exception {
        mvc.perform(get("/admin/accounts/7/password/reset"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/accounts/reset-password"))
                .andExpect(model().attributeExists(
                        "adminResetPasswordForm", "target", "cancelPath"))
                .andExpect(model().attribute("cancelPath", "/admin/accounts/students"));
    }

    @Test
    void resetPasswordValidationFailsBeforeWriting() throws Exception {
        mvc.perform(post("/admin/accounts/7/password/reset")
                        .param("newPassword", "short")
                        .param("confirmPassword", "different"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("admin/accounts/reset-password"))
                .andExpect(model().attributeHasErrors("adminResetPasswordForm"));

        verify(accounts, never()).resetPassword(any());
    }

    @Test
    void resetPasswordUsesPathAccountAndRedirectsByTargetRole() throws Exception {
        when(accounts.resetPassword(argThat(
                command -> command != null && command.accountId() == 7L)))
                .thenReturn(new PasswordResetResult(7L, "23110001", AccountType.STUDENT));
        when(accounts.resetPassword(argThat(
                command -> command != null && command.accountId() == 8L)))
                .thenReturn(new PasswordResetResult(8L, "lecturer01", AccountType.LECTURER));
        when(accounts.resetPassword(argThat(
                command -> command != null && command.accountId() == 9L)))
                .thenReturn(new PasswordResetResult(9L, "dean01", AccountType.DEAN));

        mvc.perform(post("/admin/accounts/7/password/reset")
                        .param("newPassword", "new-temporary-password")
                        .param("confirmPassword", "new-temporary-password")
                        .param("accountId", "999")
                        .param("accountType", "ADMIN")
                        .param("username", "forged"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/students"));
        mvc.perform(post("/admin/accounts/8/password/reset")
                        .param("newPassword", "new-temporary-password")
                        .param("confirmPassword", "new-temporary-password"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/lecturers"));
        mvc.perform(post("/admin/accounts/9/password/reset")
                        .param("newPassword", "new-temporary-password")
                        .param("confirmPassword", "new-temporary-password"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/deans"));

        verify(accounts).resetPassword(argThat(command ->
                command.accountId() == 7L
                        && command.newPassword().equals("new-temporary-password")));
    }

    @Test
    void reusedPasswordRendersInlineError() throws Exception {
        when(accounts.resetPassword(any())).thenThrow(new ApplicationException(
                ApplicationErrorCode.ACCOUNT_PASSWORD_REUSED));

        mvc.perform(post("/admin/accounts/7/password/reset")
                        .param("newPassword", "current-password")
                        .param("confirmPassword", "current-password"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("admin/accounts/reset-password"))
                .andExpect(model().attributeHasFieldErrors(
                        "adminResetPasswordForm", "newPassword"));
    }

    @Test
    void unknownOrAdminTargetReturnsNotFound() throws Exception {
        when(accounts.passwordResetTarget(99L)).thenThrow(new ApplicationException(
                ApplicationErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(get("/admin/accounts/99/password/reset"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void locksAndUnlocksUsingPathAccountAndRedirectsByRole() throws Exception {
        when(accounts.lockAccount(7L)).thenReturn(new AccountStatusResult(
                7L, "23110001", AccountType.STUDENT, false, true));
        when(accounts.unlockAccount(8L)).thenReturn(new AccountStatusResult(
                8L, "lecturer01", AccountType.LECTURER, true, true));
        when(accounts.lockAccount(9L)).thenReturn(new AccountStatusResult(
                9L, "dean01", AccountType.DEAN, false, true));

        mvc.perform(post("/admin/accounts/7/lock")
                        .param("accountId", "999")
                        .param("enabled", "true")
                        .param("accountType", "ADMIN"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/students"));
        mvc.perform(post("/admin/accounts/8/unlock")
                        .param("enabled", "false"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/lecturers"));
        mvc.perform(post("/admin/accounts/9/lock"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/accounts/deans"));

        verify(accounts).lockAccount(7L);
        verify(accounts).unlockAccount(8L);
        verify(accounts).lockAccount(9L);
        verify(accounts, never()).lockAccount(999L);
    }

    @Test
    void accountStatusRoutesRejectGetMutationAndInvalidTarget() throws Exception {
        MockMvc routeOnlyMvc = standaloneSetup(new AdminAccountController(accounts)).build();

        routeOnlyMvc.perform(get("/admin/accounts/7/lock"))
                .andExpect(status().isMethodNotAllowed());
        routeOnlyMvc.perform(get("/admin/accounts/7/unlock"))
                .andExpect(status().isMethodNotAllowed());

        when(accounts.lockAccount(99L)).thenThrow(new ApplicationException(
                ApplicationErrorCode.RESOURCE_NOT_FOUND));
        mvc.perform(post("/admin/accounts/99/lock"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
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

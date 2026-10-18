package vn.edu.topicregistration.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import vn.edu.topicregistration.controller.AdminDepartmentController;
import vn.edu.topicregistration.controller.WebExceptionHandler;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.AdminDepartmentService;
import vn.edu.topicregistration.service.AdminDepartmentService.DepartmentView;
import vn.edu.topicregistration.service.AdminDepartmentService.DepartmentWorkspace;
import vn.edu.topicregistration.service.AdminDepartmentService.FacultyOption;

class AdminDepartmentControllerTest {
    AdminDepartmentService departments;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        departments = mock(AdminDepartmentService.class);
        when(departments.workspace(any())).thenReturn(DepartmentWorkspace.empty());
        when(departments.facultyOptions()).thenReturn(List.of(
                new FacultyOption(1L, "Khoa Công nghệ Thông tin")));
        when(departments.department(7L)).thenReturn(new DepartmentView(
                7L, "Hệ thống thông tin", 1L, "Khoa Công nghệ Thông tin"));
        when(departments.update(anyLong(), any())).thenReturn(new DepartmentView(
                7L, "Khoa học dữ liệu", 1L, "Khoa Công nghệ Thông tin"));
        mvc = standaloneSetup(new AdminDepartmentController(departments))
                .setControllerAdvice(new WebExceptionHandler())
                .build();
    }

    @Test
    void rendersListCreateAndEditPages() throws Exception {
        mvc.perform(get("/admin/departments")
                        .param("query", "hệ thống")
                        .param("facultyId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/departments/list"))
                .andExpect(model().attributeExists("workspace"));
        mvc.perform(get("/admin/departments/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/departments/form"))
                .andExpect(model().attributeExists("departmentForm", "facultyOptions"));
        mvc.perform(get("/admin/departments/7/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/departments/form"))
                .andExpect(model().attributeExists("departmentForm", "department"));

        verify(departments).workspace(argThat(filter ->
                filter.facultyId() == 1L && filter.query().equals("hệ thống")));
    }

    @Test
    void createAndUpdateUseWhitelistedFieldsAndRedirect() throws Exception {
        mvc.perform(post("/admin/departments")
                        .param("facultyId", "1")
                        .param("name", "Hệ thống thông tin")
                        .param("id", "999")
                        .param("delete", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/departments"));
        mvc.perform(post("/admin/departments/7")
                        .param("facultyId", "1")
                        .param("name", "Khoa học dữ liệu")
                        .param("id", "999"))
                .andExpect(status().isSeeOther())
                .andExpect(redirectedUrl("/admin/departments"));

        verify(departments).create(argThat(command ->
                command.facultyId() == 1L
                        && command.name().equals("Hệ thống thông tin")));
        verify(departments).update(eq(7L), argThat(command ->
                command.facultyId() == 1L
                        && command.name().equals("Khoa học dữ liệu")));
    }

    @Test
    void invalidFieldsRenderInlineErrorsWithoutWriting() throws Exception {
        mvc.perform(post("/admin/departments")
                        .param("facultyId", "0")
                        .param("name", "   "))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("admin/departments/form"))
                .andExpect(model().attributeHasFieldErrors(
                        "departmentForm", "facultyId", "name"));

        verify(departments, never()).create(any());
    }

    @Test
    void duplicateNameIsAttachedToNameField() throws Exception {
        when(departments.create(any())).thenThrow(new ApplicationException(
                ApplicationErrorCode.DEPARTMENT_NAME_EXISTS));

        mvc.perform(post("/admin/departments")
                        .param("facultyId", "1")
                        .param("name", "Hệ thống thông tin"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(view().name("admin/departments/form"))
                .andExpect(model().attributeHasFieldErrors("departmentForm", "name"));
    }

    @Test
    void forgedFacultyOnUpdateIsRejectedAsNotFound() throws Exception {
        when(departments.update(eq(7L), argThat(command ->
                command.facultyId() == 99L))).thenThrow(new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));

        mvc.perform(post("/admin/departments/7")
                        .param("facultyId", "99")
                        .param("name", "Hệ thống thông tin"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/error"));
    }

    @Test
    void deleteRoutesAreNotMapped() throws Exception {
        MockMvc routeOnlyMvc = standaloneSetup(
                new AdminDepartmentController(departments)).build();

        routeOnlyMvc.perform(delete("/admin/departments/7"))
                .andExpect(status().isMethodNotAllowed());
        routeOnlyMvc.perform(post("/admin/departments/7/delete"))
                .andExpect(status().isNotFound());
    }
}

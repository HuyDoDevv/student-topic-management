package vn.edu.topicregistration.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.edu.topicregistration.dto.DepartmentForm;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.AdminDepartmentService;
import vn.edu.topicregistration.service.AdminDepartmentService.DepartmentFilter;
import vn.edu.topicregistration.service.AdminDepartmentService.DepartmentView;

@Controller
@Profile("!test")
public class AdminDepartmentController {
    private static final String LIST_PATH = "/admin/departments";
    private final AdminDepartmentService departments;

    public AdminDepartmentController(AdminDepartmentService departments) {
        this.departments = departments;
    }

    @InitBinder("departmentForm")
    void whitelistDepartmentFields(WebDataBinder binder) {
        binder.setAllowedFields("facultyId", "name");
    }

    @GetMapping(LIST_PATH)
    public String departments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long facultyId,
            Model model) {
        common(model);
        model.addAttribute("workspace", departments.workspace(
                new DepartmentFilter(query, facultyId)));
        return "admin/departments/list";
    }

    @GetMapping(LIST_PATH + "/new")
    public String createForm(Model model) {
        return renderCreateForm(model, new DepartmentForm());
    }

    @PostMapping(LIST_PATH)
    public Object create(
            @Valid @ModelAttribute("departmentForm") DepartmentForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderCreateForm(model, form);
        }
        try {
            departments.create(form.toCommand());
        } catch (ApplicationException exception) {
            reject(exception, bindingResult);
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderCreateForm(model, form);
        }
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã tạo Bộ môn " + form.getName().trim() + ".");
        return WebRedirects.seeOther(LIST_PATH);
    }

    @GetMapping(LIST_PATH + "/{departmentId}/edit")
    public String editForm(
            @PathVariable("departmentId") long departmentId,
            Model model) {
        DepartmentView department = departments.department(departmentId);
        return renderEditForm(model, department,
                new DepartmentForm(department.facultyId(), department.name()));
    }

    @PostMapping(LIST_PATH + "/{departmentId}")
    public Object update(
            @PathVariable("departmentId") long departmentId,
            @Valid @ModelAttribute("departmentForm") DepartmentForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes,
            Model model) {
        DepartmentView department = departments.department(departmentId);
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderEditForm(model, department, form);
        }
        DepartmentView updated;
        try {
            updated = departments.update(departmentId, form.toCommand());
        } catch (ApplicationException exception) {
            reject(exception, bindingResult);
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderEditForm(model, department, form);
        }
        redirectAttributes.addFlashAttribute(
                "successMessage", "Đã cập nhật Bộ môn " + updated.name() + ".");
        return WebRedirects.seeOther(LIST_PATH);
    }

    private String renderCreateForm(Model model, DepartmentForm form) {
        common(model);
        if (!model.containsAttribute("departmentForm")) {
            model.addAttribute("departmentForm", form);
        }
        model.addAttribute("facultyOptions", departments.facultyOptions());
        model.addAttribute("editing", false);
        model.addAttribute("formTitle", "Thêm Bộ môn");
        model.addAttribute("formDescription", "Tạo Bộ môn trong một Khoa đã có.");
        model.addAttribute("submitLabel", "Tạo Bộ môn");
        model.addAttribute("formAction", LIST_PATH);
        return "admin/departments/form";
    }

    private String renderEditForm(
            Model model, DepartmentView department, DepartmentForm form) {
        common(model);
        form.setFacultyId(department.facultyId());
        if (!model.containsAttribute("departmentForm")) {
            model.addAttribute("departmentForm", form);
        }
        model.addAttribute("department", department);
        model.addAttribute("editing", true);
        model.addAttribute("formTitle", "Sửa Bộ môn");
        model.addAttribute("formDescription", "Cập nhật tên Bộ môn trong Khoa hiện tại.");
        model.addAttribute("submitLabel", "Lưu thay đổi");
        model.addAttribute("formAction", LIST_PATH + "/" + department.id());
        return "admin/departments/form";
    }

    private static void reject(
            ApplicationException exception, BindingResult bindingResult) {
        if (exception.code() == ApplicationErrorCode.DEPARTMENT_NAME_EXISTS) {
            bindingResult.rejectValue("name", "department.name.exists",
                    "Tên Bộ môn đã tồn tại trong Khoa này.");
        } else if (exception.code() == ApplicationErrorCode.VALIDATION_FAILED) {
            bindingResult.reject("department.invalid",
                    "Thông tin Bộ môn hoặc Khoa chưa hợp lệ.");
        } else {
            throw exception;
        }
    }

    private static void common(Model model) {
        model.addAttribute("activeNav", "admin-departments");
        model.addAttribute("pageSection", "Quản trị hệ thống");
    }
}

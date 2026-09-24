package vn.edu.topicregistration.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.edu.topicregistration.dto.AnnouncementForm;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.service.AnnouncementService;
import vn.edu.topicregistration.service.AnnouncementService.AnnouncementStatusFilter;
import vn.edu.topicregistration.service.AnnouncementService.AnnouncementView;
import vn.edu.topicregistration.service.AnnouncementService.PublicationResult;

@Controller
@Profile("!preview & !test")
@RequestMapping("/announcements")
public class AnnouncementController {
    private final AnnouncementService announcements;

    public AnnouncementController(AnnouncementService announcements) {
        this.announcements = announcements;
    }

    @InitBinder("form")
    void whitelistFormFields(WebDataBinder binder) {
        binder.setAllowedFields("title", "body", "version");
    }

    @GetMapping("/manage")
    String list(
            @RequestParam(defaultValue = "ALL") AnnouncementStatusFilter status,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Page<AnnouncementView> announcementPage = announcements.listForCurrentManager(status, page);
        common(model);
        model.addAttribute("announcements", announcementPage.getContent());
        model.addAttribute("announcementPage", announcementPage);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("currentPage", page);
        model.addAttribute("displayedPage", page + 1);
        model.addAttribute("displayedTotalPages", Math.max(1, announcementPage.getTotalPages()));
        model.addAttribute("managerScope", announcements.currentManagerScope());
        return "announcements/list";
    }

    @GetMapping("/new")
    String createForm(Model model) {
        return renderForm(model, new AnnouncementForm(), false, null);
    }

    @PostMapping
    Object create(
            @Valid @ModelAttribute("form") AnnouncementForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            return renderForm(model, form, false, null);
        }
        AnnouncementView created = announcements.create(form.toCommand());
        return WebRedirects.seeOther("/announcements/" + created.id());
    }

    @GetMapping("/{id}")
    String detail(@PathVariable long id, Model model) {
        common(model);
        model.addAttribute("announcement", announcements.getVisible(id));
        return "announcements/detail";
    }

    @GetMapping("/{id}/edit")
    String editForm(@PathVariable long id, Model model) {
        AnnouncementView announcement = announcements.getForCurrentManager(id);
        if (announcement.published()) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return renderForm(model, AnnouncementForm.from(announcement), true, announcement);
    }

    @PostMapping("/{id}")
    Object update(
            @PathVariable long id,
            @Valid @ModelAttribute("form") AnnouncementForm form,
            BindingResult bindingResult,
            HttpServletResponse response,
            Model model) {
        if (form.getVersion() == null) {
            bindingResult.rejectValue("version", "announcement.version.required",
                    "Thiếu phiên bản dữ liệu. Vui lòng tải lại trang.");
        }
        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_CONTENT.value());
            AnnouncementView current = announcements.getForCurrentManager(id);
            return renderForm(model, form, true, current);
        }
        AnnouncementView updated = announcements.update(id, form.getVersion(), form.toCommand());
        return WebRedirects.seeOther("/announcements/" + updated.id());
    }

    @PostMapping("/{id}/publish")
    Object publish(
            @PathVariable long id,
            @RequestParam(required = false) Long version,
            RedirectAttributes redirectAttributes) {
        if (version == null || version < 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        PublicationResult result = announcements.publish(id, version);
        redirectAttributes.addFlashAttribute(
                "announcementResult",
                "Đã công bố thông báo tới " + result.recipientCount() + " tài khoản.");
        return WebRedirects.seeOther("/announcements/" + id);
    }

    private String renderForm(
            Model model,
            AnnouncementForm form,
            boolean editing,
            AnnouncementView announcement) {
        common(model);
        model.addAttribute("form", form);
        model.addAttribute("editing", editing);
        model.addAttribute("announcement", announcement);
        model.addAttribute("managerScope", announcements.currentManagerScope());
        return "announcements/form";
    }

    private static void common(Model model) {
        model.addAttribute("activeNav", "announcements");
        model.addAttribute("pageSection", "Thông báo chính thức");
    }
}

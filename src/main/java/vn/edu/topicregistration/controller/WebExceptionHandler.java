package vn.edu.topicregistration.controller;

import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;


@ControllerAdvice
public class WebExceptionHandler {
    @ExceptionHandler(ApplicationException.class)
    ModelAndView handleApplicationError(ApplicationException exception, HttpServletResponse response) {
        ApplicationErrorCode code = exception.code();
        return error(response, code.status(), code.name(), code.title(), code.userMessage(), List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ModelAndView handleAccessDenied(HttpServletResponse response) {
        return error(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Bạn không có quyền truy cập", "Bạn chưa được phép thực hiện thao tác này.", List.of());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ModelAndView handleNotFound(HttpServletResponse response) {
        ApplicationErrorCode code = ApplicationErrorCode.RESOURCE_NOT_FOUND;
        return error(response, code.status(), code.name(), code.title(), code.userMessage(), List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    ModelAndView handleMalformedRequest(HttpServletResponse response) {
        ApplicationErrorCode code = ApplicationErrorCode.BAD_REQUEST;
        return error(response, code.status(), code.name(), code.title(), code.userMessage(), List.of());
    }

    @ExceptionHandler(BindException.class)
    ModelAndView handleValidation(BindException exception, HttpServletResponse response) {
        List<FieldErrorView> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorView(error.getField(), safeValidationMessage(error.getDefaultMessage())))
                .toList();
        ApplicationErrorCode code = ApplicationErrorCode.VALIDATION_FAILED;
        return error(response, code.status(), code.name(), code.title(), code.userMessage(), fields);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class,
            CannotAcquireLockException.class,
            DataIntegrityViolationException.class})
    ModelAndView handlePersistenceConflict(HttpServletResponse response) {
        ApplicationErrorCode code = ApplicationErrorCode.CONFLICT;
        return error(response, code.status(), code.name(), code.title(), code.userMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    ModelAndView handleUnexpected(HttpServletResponse response) {
        return error(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Đã xảy ra lỗi hệ thống", "Hệ thống chưa thể xử lý yêu cầu. Vui lòng thử lại sau.", List.of());
    }

    private ModelAndView error(HttpServletResponse response, HttpStatus status, String errorCode,
            String title, String description, List<FieldErrorView> fieldErrors) {
        response.setStatus(status.value());
        ModelAndView result = new ModelAndView("error/error");
        result.addObject("status", status.value());
        result.addObject("errorCode", errorCode);
        result.addObject("title", title);
        result.addObject("description", description);
        result.addObject("fieldErrors", fieldErrors);
        return result;
    }

    private static String safeValidationMessage(String message) {
        return message == null || message.isBlank() ? "Giá trị không hợp lệ." : message;
    }

    public record FieldErrorView(String field, String message) {
    }
}

package vn.edu.topicregistration.service;

import java.util.Objects;
import java.util.Locale;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.PasswordPolicy;

@Service
@Profile("!test")
public class AccountSecurityService {
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AccountSecurityService(
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            PasswordEncoder passwordEncoder,
            Validator validator) {
        this.accounts = accounts;
        this.authorization = authorization;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public String currentEmail() {
        return staffAccount(false).getEmail();
    }

    @Transactional
    public void changeEmail(ChangeEmailCommand command) {
        UserAccountEntity account = staffAccount(true);
        String email = command == null || command.email() == null
                ? null : command.email().strip().toLowerCase(Locale.ROOT);
        if (!validator.validate(new ChangeEmailCommand(email)).isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        if (email.equalsIgnoreCase(account.getEmail())) {
            return;
        }
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new ApplicationException(ApplicationErrorCode.ACCOUNT_EMAIL_EXISTS);
        }

        account.changeEmail(email);
        try {
            accounts.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw new ApplicationException(ApplicationErrorCode.ACCOUNT_EMAIL_EXISTS);
            }
            throw exception;
        }
    }

    private UserAccountEntity staffAccount(boolean lock) {
        var actor = authorization.currentActor();
        if (actor.accountId() == null
                || (!actor.roles().contains(RoleCode.DEAN)
                    && !actor.roles().contains(RoleCode.LECTURER))) {
            throw new AccessDeniedException("Email updates are restricted to academic staff");
        }
        return (lock ? accounts.findByIdForUpdate(actor.accountId())
                : accounts.findDetailedById(actor.accountId()))
                .filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Account is not available"));
    }

    private static boolean isEmailUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                String constraint = violation.getConstraintName()
                        .replace("`", "").replace("'", "");
                if (constraint.equals("uq_user_accounts_email")
                        || constraint.endsWith(".uq_user_accounts_email")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        Objects.requireNonNull(command, "command");
        var actor = authorization.currentActor();
        UserAccountEntity account = accounts.findByIdForUpdate(actor.accountId())
                .filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Access denied"));

        if (!PasswordPolicy.isValid(command.newPassword())
                || !Objects.equals(command.newPassword(), command.confirmPassword())) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        if (command.currentPassword() == null
                || !PasswordPolicy.isWithinBcryptLimit(command.currentPassword())
                || !passwordEncoder.matches(
                        command.currentPassword(), account.getPasswordHash())) {
            throw new ApplicationException(
                    ApplicationErrorCode.ACCOUNT_CURRENT_PASSWORD_INVALID);
        }
        if (passwordEncoder.matches(command.newPassword(), account.getPasswordHash())) {
            throw new ApplicationException(ApplicationErrorCode.ACCOUNT_PASSWORD_REUSED);
        }

        account.changePassword(passwordEncoder.encode(command.newPassword()));
        accounts.flush();
    }

    public record ChangePasswordCommand(
            String currentPassword,
            String newPassword,
            String confirmPassword) {
    }

    public record ChangeEmailCommand(
            @NotBlank @Email @Size(max = 254) String email) {
    }
}

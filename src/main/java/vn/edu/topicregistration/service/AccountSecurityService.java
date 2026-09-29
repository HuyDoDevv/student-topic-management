package vn.edu.topicregistration.service;

import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.UserAccountEntity;
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

    public AccountSecurityService(
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.authorization = authorization;
        this.passwordEncoder = passwordEncoder;
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
}

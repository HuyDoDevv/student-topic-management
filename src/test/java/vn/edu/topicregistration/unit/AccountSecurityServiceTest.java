package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.AccountSecurityService;
import vn.edu.topicregistration.service.AccountSecurityService.ChangePasswordCommand;

class AccountSecurityServiceTest {
    @Test
    void changesPasswordClearsTemporaryFlagAndRevokesExistingSessions() {
        BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
        UserAccountEntity account = new UserAccountEntity(
                "Sinh viên Một", "23110001", passwords.encode("current-password"), null);
        account.markPasswordAsTemporary();
        Dependencies dependencies = dependencies(account, passwords);

        dependencies.service().changePassword(new ChangePasswordCommand(
                "current-password", "new-password-123", "new-password-123"));

        assertFalse(account.isMustChangePassword());
        assertEquals(1L, account.getSecurityVersion());
        assertFalse(passwords.matches("current-password", account.getPasswordHash()));
        verify(dependencies.accounts()).flush();
    }

    @Test
    void rejectsWrongCurrentPassword() {
        BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
        UserAccountEntity account = new UserAccountEntity(
                "Sinh viên Một", "23110001", passwords.encode("current-password"), null);
        Dependencies dependencies = dependencies(account, passwords);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().changePassword(new ChangePasswordCommand(
                        "wrong-password", "new-password-123", "new-password-123")));

        assertEquals(ApplicationErrorCode.ACCOUNT_CURRENT_PASSWORD_INVALID, exception.code());
        assertEquals(0L, account.getSecurityVersion());
    }

    @Test
    void rejectsReusingCurrentPassword() {
        BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
        UserAccountEntity account = new UserAccountEntity(
                "Sinh viên Một", "23110001", passwords.encode("current-password"), null);
        Dependencies dependencies = dependencies(account, passwords);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().changePassword(new ChangePasswordCommand(
                        "current-password", "current-password", "current-password")));

        assertEquals(ApplicationErrorCode.ACCOUNT_PASSWORD_REUSED, exception.code());
        assertEquals(0L, account.getSecurityVersion());
    }

    @Test
    void serviceAlsoRejectsInvalidOrMismatchedNewPassword() {
        BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
        UserAccountEntity account = new UserAccountEntity(
                "Sinh viên Một", "23110001", passwords.encode("current-password"), null);
        Dependencies dependencies = dependencies(account, passwords);

        ApplicationException shortPassword = assertThrows(ApplicationException.class,
                () -> dependencies.service().changePassword(new ChangePasswordCommand(
                        "current-password", "short", "short")));
        ApplicationException mismatch = assertThrows(ApplicationException.class,
                () -> dependencies.service().changePassword(new ChangePasswordCommand(
                        "current-password", "new-password-123", "different-password")));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, shortPassword.code());
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, mismatch.code());
    }

    private static Dependencies dependencies(
            UserAccountEntity account, BCryptPasswordEncoder passwords) {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        when(authorization.currentActor()).thenReturn(
                new ActorIdentity(7L, Set.of(RoleCode.STUDENT), 1L));
        when(accounts.findByIdForUpdate(7L)).thenReturn(Optional.of(account));
        return new Dependencies(accounts, authorization, passwords);
    }

    private record Dependencies(
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            BCryptPasswordEncoder passwords) {
        AccountSecurityService service() {
            return new AccountSecurityService(accounts, authorization, passwords);
        }
    }
}

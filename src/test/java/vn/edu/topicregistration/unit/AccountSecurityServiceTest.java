package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.sql.SQLException;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
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
import vn.edu.topicregistration.service.AccountSecurityService.ChangeEmailCommand;

class AccountSecurityServiceTest {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"DEAN", "LECTURER"})
    void staffUpdatesOnlyOwnEmailWithoutChangingCredentials(RoleCode role) {
        UserAccountEntity account = emailAccount(null);
        Dependencies dependencies = emailDependencies(account, role);

        dependencies.service().changeEmail(new ChangeEmailCommand("  Owner@Example.com  "));

        assertEquals("owner@example.com", account.getEmail());
        assertEquals("staff", account.getUsername());
        assertEquals("unchanged-password-hash", account.getPasswordHash());
        assertEquals(0L, account.getSecurityVersion());
        verify(dependencies.accounts()).findByIdForUpdate(7L);
        verify(dependencies.accounts()).flush();
    }

    @Test
    void readsCurrentEmailAndAllowsSavingItAgain() {
        UserAccountEntity account = emailAccount("owner@example.com");
        Dependencies dependencies = emailDependencies(account, RoleCode.DEAN);

        assertEquals("owner@example.com", dependencies.service().currentEmail());
        dependencies.service().changeEmail(new ChangeEmailCommand(" OWNER@example.com "));

        verify(dependencies.accounts(), never()).existsByEmailIgnoreCase("owner@example.com");
        verify(dependencies.accounts(), never()).flush();
        assertEquals("owner@example.com", account.getEmail());
    }

    @ParameterizedTest
    @EnumSource(value = RoleCode.class, names = {"STUDENT", "ADMIN"})
    void rejectsBothReadingAndUpdatingEmailForOtherRoles(RoleCode role) {
        Dependencies dependencies = emailDependencies(emailAccount(null), role);

        assertThrows(AccessDeniedException.class, () -> dependencies.service().currentEmail());
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().changeEmail(new ChangeEmailCommand("valid@example.com")));
        verifyNoInteractions(dependencies.accounts());
    }

    @ParameterizedTest
    @MethodSource("invalidEmails")
    void serviceRejectsInvalidEmailEvenWithoutControllerValidation(String input) {
        UserAccountEntity account = emailAccount("original@example.com");
        Dependencies dependencies = emailDependencies(account, RoleCode.LECTURER);

        var failure = assertThrows(ApplicationException.class,
                () -> dependencies.service().changeEmail(new ChangeEmailCommand(input)));

        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, failure.code());
        assertEquals("original@example.com", account.getEmail());
        verify(dependencies.accounts(), never()).flush();
    }

    private static Stream<String> invalidEmails() {
        return Stream.of(null, "", "   ", "not-an-email", "two addresses@example.com",
                "a".repeat(250) + "@example.com");
    }

    @Test
    void rejectsEmailUsedByAnotherAccount() {
        UserAccountEntity account = emailAccount("original@example.com");
        Dependencies dependencies = emailDependencies(account, RoleCode.DEAN);
        when(dependencies.accounts().existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        var failure = assertThrows(ApplicationException.class,
                () -> dependencies.service().changeEmail(new ChangeEmailCommand("TAKEN@example.com")));

        assertEquals(ApplicationErrorCode.ACCOUNT_EMAIL_EXISTS, failure.code());
        assertEquals("original@example.com", account.getEmail());
        verify(dependencies.accounts(), never()).flush();
    }

    @Test
    void translatesOnlyEmailUniqueConstraintFailures() {
        Dependencies dependencies = emailDependencies(emailAccount(null), RoleCode.DEAN);
        doThrow(new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate email",
                        new SQLException("duplicate", "23000", 1062),
                        "user_accounts.uq_user_accounts_email")))
                .when(dependencies.accounts()).flush();

        var failure = assertThrows(ApplicationException.class,
                () -> dependencies.service().changeEmail(new ChangeEmailCommand("race@example.com")));
        assertEquals(ApplicationErrorCode.ACCOUNT_EMAIL_EXISTS, failure.code());
    }

    @Test
    void doesNotHideUnrelatedDatabaseFailuresAsDuplicateEmail() {
        Dependencies dependencies = emailDependencies(emailAccount(null), RoleCode.DEAN);
        var failure = new DataIntegrityViolationException("unrelated check constraint");
        doThrow(failure).when(dependencies.accounts()).flush();

        assertEquals(failure, assertThrows(DataIntegrityViolationException.class,
                () -> dependencies.service().changeEmail(new ChangeEmailCommand("valid@example.com"))));
    }

    private static UserAccountEntity emailAccount(String email) {
        return new UserAccountEntity("Staff", "staff", "unchanged-password-hash", email, null);
    }

    private static Dependencies emailDependencies(UserAccountEntity account, RoleCode role) {
        Dependencies dependencies = dependencies(account, new BCryptPasswordEncoder());
        when(dependencies.authorization().currentActor()).thenReturn(
                new ActorIdentity(7L, Set.of(role), 1L));
        when(dependencies.accounts().findDetailedById(7L)).thenReturn(Optional.of(account));
        return dependencies;
    }
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
            return new AccountSecurityService(accounts, authorization, passwords, VALIDATORS.getValidator());
        }
    }
}

package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AccountDetailsService;
import vn.edu.topicregistration.security.AccountPrincipal;

class AccountDetailsServiceTest {
    @Test
    void rejectsDeanLecturerCombinationButLoadsPureDean() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(accounts.findByUsername("dean")).thenReturn(Optional.of(account));
        when(account.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));

        AccountDetailsService service = new AccountDetailsService(accounts);
        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("dean"));

        when(account.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN));
        when(account.getUsername()).thenReturn("dean");
        when(account.getPasswordHash()).thenReturn("encoded-password");
        assertEquals(Set.of(RoleCode.DEAN),
                ((AccountPrincipal) service.loadUserByUsername("dean")).roles());
    }

    @Test
    void principalCarriesPersistedPasswordAndSessionSecurityState() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserAccountEntity account = mock(UserAccountEntity.class);
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(accounts.findByUsername("23110001")).thenReturn(Optional.of(account));
        when(account.getId()).thenReturn(7L);
        when(account.getUsername()).thenReturn("23110001");
        when(account.getPasswordHash()).thenReturn("encoded-password");
        when(account.getFullName()).thenReturn("Sinh viên Một");
        when(account.isEnabled()).thenReturn(true);
        when(account.isMustChangePassword()).thenReturn(true);
        when(account.getSecurityVersion()).thenReturn(4L);
        when(account.getRoleCodes()).thenReturn(Set.of(RoleCode.STUDENT));
        when(account.getFaculty()).thenReturn(faculty);
        when(faculty.getId()).thenReturn(2L);

        AccountPrincipal principal = (AccountPrincipal) new AccountDetailsService(accounts)
                .loadUserByUsername("23110001");

        assertEquals(7L, principal.accountId());
        assertEquals(2L, principal.facultyId());
        assertEquals(4L, principal.securityVersion());
        assertTrue(principal.mustChangePassword());
        assertTrue(principal.isEnabled());
    }
}

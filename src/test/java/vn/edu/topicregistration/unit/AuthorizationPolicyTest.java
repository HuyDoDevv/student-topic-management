package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;

class AuthorizationPolicyTest {
    UserAccountRepository accounts;
    AuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        policy = new AuthorizationPolicy(accounts);
        authenticateAs("actor", "ROLE_ADMIN");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ignoresAuthoritiesClaimedByTheSessionAndReloadsPersistedRoles() {
        stubAccount(7L, true, Set.of(RoleCode.STUDENT), 21L);

        assertThrows(AccessDeniedException.class, () -> policy.requireEmergencyAdmin());
        assertEquals(7L, policy.requireStudentOfFaculty(21L).accountId());
    }

    @Test
    void lecturerDeanCombinationIsValidAndBothScopesComeFromTheDatabase() {
        stubAccount(7L, true, Set.of(RoleCode.LECTURER, RoleCode.DEAN), 21L);

        assertEquals(7L, policy.requireLecturerOfFaculty(21L).accountId());
        assertEquals(7L, policy.requireDeanOfFaculty(21L).accountId());
        assertThrows(AccessDeniedException.class, () -> policy.requireLecturerOfFaculty(22L));
        assertThrows(AccessDeniedException.class, () -> policy.requireDeanOfFaculty(22L));
    }

    @Test
    void authorAndLeaderChecksUsePersistedRelationshipIds() {
        stubAccount(7L, true, Set.of(RoleCode.LECTURER), 21L);
        assertEquals(7L, policy.requireTopicAuthor(7L, 21L).accountId());
        assertThrows(AccessDeniedException.class, () -> policy.requireTopicAuthor(8L, 21L));

        stubAccount(7L, true, Set.of(RoleCode.STUDENT), 21L);
        assertEquals(7L, policy.requireGroupLeader(7L).accountId());
        assertThrows(AccessDeniedException.class, () -> policy.requireGroupLeader(8L));
    }

    @Test
    void adminHasOnlyEmergencyAuthority() {
        stubAccount(7L, true, Set.of(RoleCode.ADMIN), null);

        assertEquals(7L, policy.requireEmergencyAdmin().accountId());
        assertThrows(AccessDeniedException.class, () -> policy.requireDeanOfFaculty(21L));
        assertThrows(AccessDeniedException.class, () -> policy.requireGroupLeader(7L));
    }

    @Test
    void missingDisabledOrInvalidRoleAssignmentsFailClosed() {
        when(accounts.findByUsername("actor")).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class, policy::currentActor);

        stubAccount(7L, false, Set.of(RoleCode.LECTURER), 21L);
        assertThrows(AccessDeniedException.class, policy::currentActor);

        stubAccount(7L, true, Set.of(RoleCode.STUDENT, RoleCode.LECTURER), 21L);
        assertThrows(AccessDeniedException.class, policy::currentActor);
    }

    private void authenticateAs(String username, String claimedAuthority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "unused",
                        Set.of(new SimpleGrantedAuthority(claimedAuthority))));
    }

    private UserAccountEntity account(Long id, boolean enabled, Set<RoleCode> roles,
            Long facultyId) {
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(id);
        when(account.isEnabled()).thenReturn(enabled);
        when(account.getRoleCodes()).thenReturn(roles);
        if (facultyId != null) {
            FacultyEntity faculty = mock(FacultyEntity.class);
            when(faculty.getId()).thenReturn(facultyId);
            when(account.getFaculty()).thenReturn(faculty);
        }
        return account;
    }

    private void stubAccount(Long id, boolean enabled, Set<RoleCode> roles,
            Long facultyId) {
        UserAccountEntity account = account(id, enabled, roles, facultyId);
        when(accounts.findByUsername("actor")).thenReturn(Optional.of(account));
    }
}

package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.RoleEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserRoleEntity;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.FacultyRepository;
import vn.edu.topicregistration.repository.RoleRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserRoleRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.AccountType;
import vn.edu.topicregistration.service.AdminAccountService.CreateAccountCommand;

class AdminAccountServiceTest {

    @Test
    void createsDeanWithLecturerRoleAndEncodedPassword() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        DepartmentEntity department = department(7L, "Công nghệ phần mềm", 3L, "CNTT");
        RoleEntity deanRole = role(RoleCode.DEAN);
        RoleEntity lecturerRole = role(RoleCode.LECTURER);
        when(dependencies.departments().findById(7L)).thenReturn(Optional.of(department));
        when(dependencies.accounts().existsByUsernameIgnoreCase("dean01")).thenReturn(false);
        when(dependencies.roles().findAllById(anyIterable())).thenReturn(List.of(
                deanRole, lecturerRole));
        when(dependencies.accounts().saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(dependencies.userRoles().saveAllAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var created = dependencies.service().create(new CreateAccountCommand(
                AccountType.DEAN, " dean01 ", " Trưởng khoa Một ", "temporary-password", 7L));

        assertEquals(AccountType.DEAN, created.accountType());
        assertEquals("Trưởng khoa", created.accountTypeLabel());

        ArgumentCaptor<UserAccountEntity> account = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(dependencies.accounts()).saveAndFlush(account.capture());
        assertEquals("dean01", account.getValue().getUsername());
        assertEquals("Trưởng khoa Một", account.getValue().getFullName());
        assertTrue(dependencies.passwords().matches(
                "temporary-password", account.getValue().getPasswordHash()));
        assertFalse(account.getValue().getPasswordHash().contains("temporary-password"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UserRoleEntity>> assignments =
                ArgumentCaptor.forClass(Iterable.class);
        verify(dependencies.userRoles()).saveAllAndFlush(assignments.capture());
        Set<RoleCode> assigned = java.util.stream.StreamSupport
                .stream(assignments.getValue().spliterator(), false)
                .map(UserRoleEntity::getRole)
                .map(RoleEntity::getCode)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(RoleCode.DEAN, RoleCode.LECTURER), assigned);
    }

    @Test
    void rejectsNonAdminBeforeEveryAccountManagementOperation() {
        Dependencies dependencies = dependencies();
        when(dependencies.authorization().requireRole(RoleCode.ADMIN))
                .thenThrow(new AccessDeniedException("not admin"));
        AdminAccountService service = dependencies.service();

        assertThrows(AccessDeniedException.class, () -> service.workspace(null));
        assertThrows(AccessDeniedException.class, () -> service.create(null));

        verify(dependencies.authorization(), times(2)).requireRole(RoleCode.ADMIN);
        verifyNoInteractions(dependencies.accounts(), dependencies.userRoles(),
                dependencies.roles(), dependencies.departments(), dependencies.faculties());
    }

    private static void allowAdmin(Dependencies dependencies) {
        when(dependencies.authorization().requireRole(RoleCode.ADMIN)).thenReturn(
                new ActorIdentity(1L, Set.of(RoleCode.ADMIN), null, null));
    }

    private static DepartmentEntity department(
            long id, String name, long facultyId, String facultyName) {
        DepartmentEntity department = mock(DepartmentEntity.class);
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(department.getId()).thenReturn(id);
        when(department.getName()).thenReturn(name);
        when(department.getFaculty()).thenReturn(faculty);
        when(faculty.getId()).thenReturn(facultyId);
        when(faculty.getName()).thenReturn(facultyName);
        return department;
    }

    private static RoleEntity role(RoleCode code) {
        RoleEntity role = mock(RoleEntity.class);
        when(role.getCode()).thenReturn(code);
        return role;
    }

    private static Dependencies dependencies() {
        return new Dependencies(
                mock(UserAccountRepository.class),
                mock(UserRoleRepository.class),
                mock(RoleRepository.class),
                mock(DepartmentRepository.class),
                mock(FacultyRepository.class),
                mock(AuthorizationPolicy.class),
                new BCryptPasswordEncoder());
    }

    private record Dependencies(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            RoleRepository roles,
            DepartmentRepository departments,
            FacultyRepository faculties,
            AuthorizationPolicy authorization,
            BCryptPasswordEncoder passwords) {
        AdminAccountService service() {
            return new AdminAccountService(
                    accounts, userRoles, roles, departments, faculties,
                    authorization, passwords);
        }
    }
}

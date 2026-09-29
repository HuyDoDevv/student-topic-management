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

import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.RoleEntity;
import vn.edu.topicregistration.entity.StudentProfileEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserRoleEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.FacultyRepository;
import vn.edu.topicregistration.repository.RoleRepository;
import vn.edu.topicregistration.repository.StudentProfileRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserRoleRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.CreateDeanCommand;
import vn.edu.topicregistration.service.AdminAccountService.CreateLecturerCommand;
import vn.edu.topicregistration.service.AdminAccountService.CreateStudentCommand;

class AdminAccountServiceTest {

    @Test
    void createsDeanWithLecturerRoleAndEncodedPassword() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        allowDefaultFaculty(dependencies);
        RoleEntity deanRole = role(RoleCode.DEAN);
        RoleEntity lecturerRole = role(RoleCode.LECTURER);
        when(dependencies.roles().findAllById(anyIterable())).thenReturn(List.of(
                deanRole, lecturerRole));
        saveArguments(dependencies);

        var created = dependencies.service().createDean(new CreateDeanCommand(
                " dean01 ", " Trưởng khoa Một ", "temporary-password"));

        assertEquals("dean01", created.username());
        assertEquals("Trưởng khoa", created.accountTypeLabel());
        assertEquals(null, created.email());
        assertAssignedRoles(dependencies, Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        assertStoredPassword(dependencies, "temporary-password");
    }

    @Test
    void createsLecturerWithoutEmail() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        allowDefaultFaculty(dependencies);
        RoleEntity lecturerRole = role(RoleCode.LECTURER);
        when(dependencies.roles().findAllById(anyIterable()))
                .thenReturn(List.of(lecturerRole));
        saveArguments(dependencies);

        var created = dependencies.service().createLecturer(new CreateLecturerCommand(
                "lecturer01", "Giảng viên Một", "temporary-password"));

        assertEquals("lecturer01", created.username());
        assertEquals(null, created.email());
        assertAssignedRoles(dependencies, Set.of(RoleCode.LECTURER));
    }

    @Test
    void createsStudentWithMssvUsernameGeneratedEmailAndProfile() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        allowDefaultFaculty(dependencies);
        RoleEntity studentRole = role(RoleCode.STUDENT);
        when(dependencies.roles().findAllById(anyIterable()))
                .thenReturn(List.of(studentRole));
        when(dependencies.studentProfiles().existsByStudentCodeIgnoreCase("23110001"))
                .thenReturn(false);
        saveArguments(dependencies);
        when(dependencies.studentProfiles().saveAndFlush(any()))
                .thenAnswer(call -> call.getArgument(0));

        var created = dependencies.service().createStudent(new CreateStudentCommand(
                " 23110001 ", " Sinh viên Một ", " 23110CL1 ", " k2023 ",
                "temporary-password"));

        assertEquals("23110001", created.username());
        assertEquals("23110001", created.studentCode());
        assertEquals("23110CL1", created.className());
        assertEquals("K2023", created.cohortCode());
        assertEquals("23110001@student.hcmute.edu.vn", created.email());
        assertAssignedRoles(dependencies, Set.of(RoleCode.STUDENT));

        ArgumentCaptor<StudentProfileEntity> profile =
                ArgumentCaptor.forClass(StudentProfileEntity.class);
        verify(dependencies.studentProfiles()).saveAndFlush(profile.capture());
        assertEquals("23110001", profile.getValue().getStudentCode());
        assertEquals("23110CL1", profile.getValue().getClassName());
        assertEquals("K2023", profile.getValue().getCohortCode());
    }

    @Test
    void rejectsDuplicateStudentCodeBeforeWriting() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        when(dependencies.studentProfiles().existsByStudentCodeIgnoreCase("23110001"))
                .thenReturn(true);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> dependencies.service().createStudent(new CreateStudentCommand(
                        "23110001", "Sinh viên Một", "23110CL1", "K2023",
                        "temporary-password")));

        assertEquals(ApplicationErrorCode.ACCOUNT_STUDENT_CODE_EXISTS, exception.code());
        verifyNoInteractions(dependencies.faculties(), dependencies.roles(),
                dependencies.userRoles());
    }

    @Test
    void rejectsNonAdminBeforeEveryAccountManagementOperation() {
        Dependencies dependencies = dependencies();
        when(dependencies.authorization().requireRole(RoleCode.ADMIN))
                .thenThrow(new AccessDeniedException("not admin"));
        AdminAccountService service = dependencies.service();

        assertThrows(AccessDeniedException.class, () -> service.workspace(null));
        assertThrows(AccessDeniedException.class, () -> service.createStudent(null));
        assertThrows(AccessDeniedException.class, () -> service.createLecturer(null));
        assertThrows(AccessDeniedException.class, () -> service.createDean(null));

        verify(dependencies.authorization(), times(4)).requireRole(RoleCode.ADMIN);
        verifyNoInteractions(dependencies.accounts(), dependencies.userRoles(),
                dependencies.roles(), dependencies.studentProfiles(), dependencies.faculties());
    }

    private static void assertStoredPassword(Dependencies dependencies, String rawPassword) {
        ArgumentCaptor<UserAccountEntity> account = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(dependencies.accounts()).saveAndFlush(account.capture());
        assertTrue(dependencies.passwords().matches(
                rawPassword, account.getValue().getPasswordHash()));
        assertFalse(account.getValue().getPasswordHash().contains(rawPassword));
    }

    private static void assertAssignedRoles(
            Dependencies dependencies, Set<RoleCode> expected) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UserRoleEntity>> assignments =
                ArgumentCaptor.forClass(Iterable.class);
        verify(dependencies.userRoles()).saveAllAndFlush(assignments.capture());
        Set<RoleCode> assigned = java.util.stream.StreamSupport
                .stream(assignments.getValue().spliterator(), false)
                .map(UserRoleEntity::getRole)
                .map(RoleEntity::getCode)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, assigned);
    }

    private static void saveArguments(Dependencies dependencies) {
        when(dependencies.accounts().existsByUsernameIgnoreCase(any())).thenReturn(false);
        when(dependencies.accounts().existsByEmailIgnoreCase(any())).thenReturn(false);
        when(dependencies.accounts().saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(dependencies.userRoles().saveAllAndFlush(any()))
                .thenAnswer(call -> call.getArgument(0));
    }

    private static void allowAdmin(Dependencies dependencies) {
        when(dependencies.authorization().requireRole(RoleCode.ADMIN)).thenReturn(
                new ActorIdentity(1L, Set.of(RoleCode.ADMIN), null));
    }

    private static void allowDefaultFaculty(Dependencies dependencies) {
        FacultyEntity defaultFaculty = faculty(
                3L, AdminAccountService.DEFAULT_FACULTY_NAME);
        when(dependencies.faculties().findByNameIgnoreCase(
                AdminAccountService.DEFAULT_FACULTY_NAME))
                .thenReturn(Optional.of(defaultFaculty));
    }

    private static FacultyEntity faculty(long id, String name) {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(id);
        when(faculty.getName()).thenReturn(name);
        return faculty;
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
                mock(StudentProfileRepository.class),
                mock(FacultyRepository.class),
                mock(AuthorizationPolicy.class),
                new BCryptPasswordEncoder());
    }

    private record Dependencies(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            RoleRepository roles,
            StudentProfileRepository studentProfiles,
            FacultyRepository faculties,
            AuthorizationPolicy authorization,
            BCryptPasswordEncoder passwords) {
        AdminAccountService service() {
            return new AdminAccountService(
                    accounts, userRoles, roles, studentProfiles, faculties,
                    authorization, passwords);
        }
    }
}

package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.RoleEntity;
import vn.edu.topicregistration.entity.StudentProfileEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserRoleEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.FacultyRepository;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.RoleRepository;
import vn.edu.topicregistration.repository.StudentProfileRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserRoleRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.AdminAccountService;
import vn.edu.topicregistration.service.AdminAccountService.AccountFilter;
import vn.edu.topicregistration.service.AdminAccountService.AccountType;
import vn.edu.topicregistration.service.AdminAccountService.CreateDeanCommand;
import vn.edu.topicregistration.service.AdminAccountService.CreateLecturerCommand;
import vn.edu.topicregistration.service.AdminAccountService.CreateStudentCommand;
import vn.edu.topicregistration.service.AdminAccountService.ResetPasswordCommand;

class AdminAccountServiceTest {

    @Test
    void workspaceShowsAndFiltersStaffByDepartmentWithinFaculty() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        FacultyEntity faculty = faculty(3L, "Khoa CNTT");
        FacultyEntity otherFaculty = faculty(4L, "Khoa khác");
        DepartmentEntity software = mock(DepartmentEntity.class);
        DepartmentEntity networks = mock(DepartmentEntity.class);
        when(software.getId()).thenReturn(11L);
        when(software.getName()).thenReturn("Công nghệ phần mềm");
        when(software.getFaculty()).thenReturn(faculty);
        when(networks.getId()).thenReturn(12L);
        when(networks.getName()).thenReturn("Mạng máy tính");
        when(networks.getFaculty()).thenReturn(faculty);
        when(dependencies.departments().findAllDetailed()).thenReturn(List.of(software, networks));
        UserAccountEntity first = mock(UserAccountEntity.class);
        when(first.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(first.getId()).thenReturn(21L);
        when(first.getUsername()).thenReturn("lecturer-software");
        when(first.getFaculty()).thenReturn(faculty);
        when(first.getDepartment()).thenReturn(software);
        UserAccountEntity second = mock(UserAccountEntity.class);
        when(second.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(second.getId()).thenReturn(22L);
        when(second.getUsername()).thenReturn("lecturer-networks");
        when(second.getFaculty()).thenReturn(faculty);
        when(second.getDepartment()).thenReturn(networks);
        UserAccountEntity outside = mock(UserAccountEntity.class);
        when(outside.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(outside.getId()).thenReturn(23L);
        when(outside.getUsername()).thenReturn("lecturer-outside");
        when(outside.getFaculty()).thenReturn(otherFaculty);
        when(dependencies.accounts().findAllDetailed()).thenReturn(List.of(first, second, outside));

        var workspace = dependencies.service().workspace(
                new AccountFilter(null, AccountType.LECTURER, 3L, 11L, null));

        assertEquals(List.of(21L), workspace.accounts().stream().map(account -> account.id()).toList());
        assertEquals("Công nghệ phần mềm", workspace.accounts().getFirst().departmentName());
        assertEquals(List.of(11L, 12L), workspace.departments().stream()
                .map(option -> option.id()).toList());
    }

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
                " dean01 ", " Trưởng khoa Một ", "temporary-password", 11L));

        assertEquals("dean01", created.username());
        assertEquals("Trưởng khoa", created.accountTypeLabel());
        assertEquals(null, created.email());
        assertAssignedRoles(dependencies, Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        assertStoredPassword(dependencies, "temporary-password");
        assertTemporaryPassword(dependencies);
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
                "lecturer01", "Giảng viên Một", "temporary-password", 11L));

        assertEquals("lecturer01", created.username());
        assertEquals(null, created.email());
        assertAssignedRoles(dependencies, Set.of(RoleCode.LECTURER));
        assertTemporaryPassword(dependencies);
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
                " 23110001 ", " Sinh viên Một ", " 23110cl1 ", " k2023 ",
                "temporary-password"));

        assertEquals("23110001", created.username());
        assertEquals("23110001", created.studentCode());
        assertEquals("23110CL1", created.className());
        assertEquals("K2023", created.cohortCode());
        assertEquals("23110001@student.hcmute.edu.vn", created.email());
        assertAssignedRoles(dependencies, Set.of(RoleCode.STUDENT));
        assertTemporaryPassword(dependencies);

        ArgumentCaptor<StudentProfileEntity> profile =
                ArgumentCaptor.forClass(StudentProfileEntity.class);
        verify(dependencies.studentProfiles()).saveAndFlush(profile.capture());
        assertEquals("23110001", profile.getValue().getStudentCode());
        assertEquals("23110CL1", profile.getValue().getClassName());
        assertEquals("K2023", profile.getValue().getCohortCode());
    }

    @Test
    void staffCreationRequiresDepartmentInDefaultFaculty() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        allowDefaultFaculty(dependencies);
        saveArguments(dependencies);
        RoleEntity lecturerRole = role(RoleCode.LECTURER);
        when(dependencies.roles().findAllById(anyIterable()))
                .thenReturn(List.of(lecturerRole));

        var created = dependencies.service().createLecturer(new CreateLecturerCommand(
                "lecturer02", "Giảng viên Hai", "temporary-password", 11L));

        assertEquals("lecturer02", created.username());
        ArgumentCaptor<UserAccountEntity> account = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(dependencies.accounts()).saveAndFlush(account.capture());
        assertEquals(11L, account.getValue().getDepartment().getId());

        ApplicationException missing = assertThrows(ApplicationException.class,
                () -> dependencies.service().createLecturer(new CreateLecturerCommand(
                        "lecturer03", "Giảng viên Ba", "temporary-password", null)));
        ApplicationException wrongFaculty = assertThrows(ApplicationException.class,
                () -> dependencies.service().createDean(new CreateDeanCommand(
                        "dean03", "Trưởng khoa Ba", "temporary-password", 99L)));
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, missing.code());
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, wrongFaculty.code());
    }

    @Test
    void assignsDepartmentOnlyOnceToManagedStaff() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        FacultyEntity faculty = faculty(3L, AdminAccountService.DEFAULT_FACULTY_NAME);
        DepartmentEntity department = new DepartmentEntity(faculty, "Công nghệ phần mềm");
        UserAccountEntity lecturer = mock(UserAccountEntity.class);
        when(lecturer.getId()).thenReturn(7L);
        when(lecturer.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(lecturer.getFaculty()).thenReturn(faculty);
        when(lecturer.getUsername()).thenReturn("lecturer01");
        when(dependencies.accounts().findByIdForUpdate(7L)).thenReturn(Optional.of(lecturer));
        when(dependencies.departments().findByIdAndFacultyId(11L, 3L))
                .thenReturn(Optional.of(department));

        dependencies.service().assignStaffDepartment(7L, 11L);

        verify(lecturer).assignDepartment(department);
        verify(dependencies.accounts()).flush();

        DepartmentEntity existing = mock(DepartmentEntity.class);
        when(existing.getId()).thenReturn(12L);
        when(lecturer.getDepartment()).thenReturn(existing);
        ApplicationException transfer = assertThrows(ApplicationException.class,
                () -> dependencies.service().assignStaffDepartment(7L, 11L));
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, transfer.code());
    }

    @Test
    void rejectsStudentDepartmentAssignmentAndCrossFacultyId() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        FacultyEntity faculty = faculty(3L, AdminAccountService.DEFAULT_FACULTY_NAME);
        UserAccountEntity student = mock(UserAccountEntity.class);
        when(student.getRoleCodes()).thenReturn(Set.of(RoleCode.STUDENT));
        when(dependencies.accounts().findByIdForUpdate(7L)).thenReturn(Optional.of(student));
        ApplicationException wrongRole = assertThrows(ApplicationException.class,
                () -> dependencies.service().assignStaffDepartment(7L, 11L));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, wrongRole.code());
        verify(student, never()).assignDepartment(any());

        UserAccountEntity dean = mock(UserAccountEntity.class);
        when(dean.getRoleCodes()).thenReturn(Set.of(RoleCode.DEAN, RoleCode.LECTURER));
        when(dean.getFaculty()).thenReturn(faculty);
        when(dependencies.accounts().findByIdForUpdate(8L)).thenReturn(Optional.of(dean));
        ApplicationException wrongFaculty = assertThrows(ApplicationException.class,
                () -> dependencies.service().assignStaffDepartment(8L, 99L));
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, wrongFaculty.code());
        verify(dean, never()).assignDepartment(any());
        verify(dependencies.accounts(), never()).flush();
    }

    @Test
    void rejectsInvalidStudentFieldsBeforeWriting() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        AdminAccountService service = dependencies.service();

        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "sdfsdf", "Sinh viên Một", "23110CL1", "K2023",
                        "temporary-password")));
        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "2311000", "Sinh viên Một", "23110CL1", "K2023",
                        "temporary-password")));
        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "23110001", "Sinh viên Một", "23110 CL1", "K2023",
                        "temporary-password")));
        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "23110001", "Sinh viên Một", "A".repeat(65), "K2023",
                        "temporary-password")));
        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "23110001", "Sinh viên Một", "23110CL1", "2023",
                        "temporary-password")));
        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "23110001", " ", "23110CL1", "K2023",
                        "temporary-password")));
        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "23110001", "A".repeat(256), "23110CL1", "K2023",
                        "temporary-password")));
        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "23110001", "Sinh viên Một", "23110CL1", "K2023", "short")));
        assertThrows(IllegalArgumentException.class, () -> service.createStudent(
                new CreateStudentCommand(
                        "23110001", "Sinh viên Một", "23110CL1", "K2023",
                        "a".repeat(73))));

        verifyNoInteractions(dependencies.accounts(), dependencies.userRoles(),
                dependencies.roles(), dependencies.studentProfiles(), dependencies.faculties());
    }

    @Test
    void rejectsInvalidLecturerAndDeanFieldsBeforeWriting() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        AdminAccountService service = dependencies.service();

        assertThrows(IllegalArgumentException.class, () -> service.createLecturer(
                new CreateLecturerCommand(
                        ".lecturer", "Giảng viên Một", "temporary-password", 11L)));
        assertThrows(IllegalArgumentException.class, () -> service.createLecturer(
                new CreateLecturerCommand(
                        "a".repeat(192), "Giảng viên Một", "temporary-password", 11L)));
        assertThrows(IllegalArgumentException.class, () -> service.createLecturer(
                new CreateLecturerCommand(
                        "lecturer01", " ", "temporary-password", 11L)));
        assertThrows(IllegalArgumentException.class, () -> service.createLecturer(
                new CreateLecturerCommand(
                        "lecturer01", "A".repeat(256), "temporary-password", 11L)));
        assertThrows(IllegalArgumentException.class, () -> service.createDean(
                new CreateDeanCommand("dean-", "Trưởng khoa Một", "temporary-password", 11L)));
        assertThrows(IllegalArgumentException.class, () -> service.createDean(
                new CreateDeanCommand("dean01", "Trưởng khoa Một", "short", 11L)));
        assertThrows(IllegalArgumentException.class, () -> service.createDean(
                new CreateDeanCommand("dean01", "Trưởng khoa Một", "a".repeat(73), 11L)));
        assertThrows(IllegalArgumentException.class, () -> service.createDean(
                new CreateDeanCommand(
                        "dean01", "Trưởng khoa Một", "😀".repeat(30), 11L)));

        verifyNoInteractions(dependencies.accounts(), dependencies.userRoles(),
                dependencies.roles(), dependencies.studentProfiles(), dependencies.faculties());
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
    void resetsManagedAccountWithEncodedTemporaryPassword() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        UserAccountEntity account = mock(UserAccountEntity.class);
        when(account.getId()).thenReturn(7L);
        when(account.getUsername()).thenReturn("lecturer01");
        when(account.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(account.getPasswordHash())
                .thenReturn(dependencies.passwords().encode("current-password"));
        when(dependencies.accounts().findByIdForUpdate(7L))
                .thenReturn(Optional.of(account));

        var result = dependencies.service().resetPassword(new ResetPasswordCommand(
                7L, "new-temporary-password", "new-temporary-password"));

        assertEquals(7L, result.id());
        assertEquals("lecturer01", result.username());
        assertEquals(AdminAccountService.AccountType.LECTURER, result.accountType());
        ArgumentCaptor<String> encodedPassword = ArgumentCaptor.forClass(String.class);
        verify(account).resetPassword(encodedPassword.capture());
        assertTrue(dependencies.passwords().matches(
                "new-temporary-password", encodedPassword.getValue()));
        verify(dependencies.accounts()).flush();
    }

    @Test
    void entityResetMarksPasswordTemporaryAndRevokesExistingSessions() {
        UserAccountEntity account = new UserAccountEntity(
                "Sinh viên Một", "23110001", "old-hash", null);

        account.resetPassword("new-hash");

        assertEquals("new-hash", account.getPasswordHash());
        assertTrue(account.isMustChangePassword());
        assertEquals(1L, account.getSecurityVersion());
    }

    @Test
    void rejectsInvalidReusedAndAdminTargetPasswords() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        AdminAccountService service = dependencies.service();

        ApplicationException invalid = assertThrows(ApplicationException.class,
                () -> service.resetPassword(new ResetPasswordCommand(
                        7L, "short", "different")));
        assertEquals(ApplicationErrorCode.VALIDATION_FAILED, invalid.code());
        verify(dependencies.accounts(), never()).findByIdForUpdate(7L);

        UserAccountEntity managed = mock(UserAccountEntity.class);
        when(managed.getRoleCodes()).thenReturn(Set.of(RoleCode.STUDENT));
        when(managed.getPasswordHash())
                .thenReturn(dependencies.passwords().encode("current-password"));
        when(dependencies.accounts().findByIdForUpdate(7L))
                .thenReturn(Optional.of(managed));
        ApplicationException reused = assertThrows(ApplicationException.class,
                () -> service.resetPassword(new ResetPasswordCommand(
                        7L, "current-password", "current-password")));
        assertEquals(ApplicationErrorCode.ACCOUNT_PASSWORD_REUSED, reused.code());
        verify(managed, never()).resetPassword(any());

        UserAccountEntity admin = mock(UserAccountEntity.class);
        when(admin.getRoleCodes()).thenReturn(Set.of(RoleCode.ADMIN));
        when(dependencies.accounts().findByIdForUpdate(8L))
                .thenReturn(Optional.of(admin));
        when(dependencies.accounts().findDetailedById(8L))
                .thenReturn(Optional.of(admin));
        ApplicationException resetAdmin = assertThrows(ApplicationException.class,
                () -> service.resetPassword(new ResetPasswordCommand(
                        8L, "new-temporary-password", "new-temporary-password")));
        ApplicationException viewAdmin = assertThrows(ApplicationException.class,
                () -> service.passwordResetTarget(8L));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, resetAdmin.code());
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, viewAdmin.code());
        verify(admin, never()).resetPassword(any());
    }

    @Test
    void locksAndUnlocksManagedAccountsWithRowLock() {
        Dependencies lockDependencies = dependencies();
        allowAdmin(lockDependencies);
        UserAccountEntity student = mock(UserAccountEntity.class);
        when(student.getId()).thenReturn(7L);
        when(student.getUsername()).thenReturn("23110001");
        when(student.getRoleCodes()).thenReturn(Set.of(RoleCode.STUDENT));
        when(student.disable()).thenReturn(true);
        when(lockDependencies.accounts().findByIdForUpdate(7L))
                .thenReturn(Optional.of(student));

        var locked = lockDependencies.service().lockAccount(7L);

        assertFalse(locked.enabled());
        assertTrue(locked.changed());
        assertEquals(AdminAccountService.AccountType.STUDENT, locked.accountType());
        verify(student).disable();
        verify(lockDependencies.accounts()).flush();

        Dependencies unlockDependencies = dependencies();
        allowAdmin(unlockDependencies);
        UserAccountEntity lecturer = mock(UserAccountEntity.class);
        when(lecturer.getId()).thenReturn(8L);
        when(lecturer.getUsername()).thenReturn("lecturer01");
        when(lecturer.getRoleCodes()).thenReturn(Set.of(RoleCode.LECTURER));
        when(lecturer.enable()).thenReturn(true);
        when(unlockDependencies.accounts().findByIdForUpdate(8L))
                .thenReturn(Optional.of(lecturer));

        var unlocked = unlockDependencies.service().unlockAccount(8L);

        assertTrue(unlocked.enabled());
        assertTrue(unlocked.changed());
        assertEquals(AdminAccountService.AccountType.LECTURER, unlocked.accountType());
        verify(lecturer).enable();
        verify(unlockDependencies.accounts()).flush();
    }

    @Test
    void accountStatusTransitionsAreIdempotentAndPreservePasswordState() {
        UserAccountEntity account = new UserAccountEntity(
                "Sinh viên Một", "23110001", "encoded-password", null);
        account.markPasswordAsTemporary();

        assertTrue(account.disable());
        assertFalse(account.disable());
        assertFalse(account.isEnabled());
        assertEquals(1L, account.getSecurityVersion());
        assertEquals("encoded-password", account.getPasswordHash());
        assertTrue(account.isMustChangePassword());

        assertTrue(account.enable());
        assertFalse(account.enable());
        assertTrue(account.isEnabled());
        assertEquals(2L, account.getSecurityVersion());
        assertEquals("encoded-password", account.getPasswordHash());
        assertTrue(account.isMustChangePassword());
    }

    @Test
    void repeatedStatusAndAdminTargetDoNotMutateAccount() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        UserAccountEntity student = mock(UserAccountEntity.class);
        when(student.getId()).thenReturn(7L);
        when(student.getUsername()).thenReturn("23110001");
        when(student.getRoleCodes()).thenReturn(Set.of(RoleCode.STUDENT));
        when(student.disable()).thenReturn(false);
        when(dependencies.accounts().findByIdForUpdate(7L))
                .thenReturn(Optional.of(student));

        var unchanged = dependencies.service().lockAccount(7L);

        assertFalse(unchanged.changed());
        verify(dependencies.accounts(), never()).flush();

        UserAccountEntity admin = mock(UserAccountEntity.class);
        when(admin.getRoleCodes()).thenReturn(Set.of(RoleCode.ADMIN));
        when(dependencies.accounts().findByIdForUpdate(8L))
                .thenReturn(Optional.of(admin));
        ApplicationException rejected = assertThrows(ApplicationException.class,
                () -> dependencies.service().unlockAccount(8L));
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, rejected.code());
        verify(admin, never()).enable();
    }

    @Test
    void missingAccountCannotBeLockedOrUnlocked() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        when(dependencies.accounts().findByIdForUpdate(404L))
                .thenReturn(Optional.empty());

        ApplicationException lockFailure = assertThrows(ApplicationException.class,
                () -> dependencies.service().lockAccount(404L));
        ApplicationException unlockFailure = assertThrows(ApplicationException.class,
                () -> dependencies.service().unlockAccount(404L));

        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, lockFailure.code());
        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, unlockFailure.code());
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
        assertThrows(AccessDeniedException.class, () -> service.staffDepartmentOptions());
        assertThrows(AccessDeniedException.class, () -> service.staffDepartmentOptionsForAccount(7L));
        assertThrows(AccessDeniedException.class, () -> service.staffDepartmentTarget(7L));
        assertThrows(AccessDeniedException.class, () -> service.assignStaffDepartment(7L, 11L));
        assertThrows(AccessDeniedException.class, () -> service.passwordResetTarget(7L));
        assertThrows(AccessDeniedException.class, () -> service.resetPassword(null));
        assertThrows(AccessDeniedException.class, () -> service.lockAccount(7L));
        assertThrows(AccessDeniedException.class, () -> service.unlockAccount(7L));

        verify(dependencies.authorization(), times(12)).requireRole(RoleCode.ADMIN);
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

    private static void assertTemporaryPassword(Dependencies dependencies) {
        ArgumentCaptor<UserAccountEntity> account = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(dependencies.accounts()).saveAndFlush(account.capture());
        assertTrue(account.getValue().isMustChangePassword());
        assertEquals(0L, account.getValue().getSecurityVersion());
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
        DepartmentEntity department = mock(DepartmentEntity.class);
        when(department.getId()).thenReturn(11L);
        when(department.getFaculty()).thenReturn(defaultFaculty);
        when(dependencies.departments().findByIdAndFacultyId(11L, 3L))
                .thenReturn(Optional.of(department));
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
                mock(DepartmentRepository.class),
                mock(AuthorizationPolicy.class),
                new BCryptPasswordEncoder());
    }

    private record Dependencies(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            RoleRepository roles,
            StudentProfileRepository studentProfiles,
            FacultyRepository faculties,
            DepartmentRepository departments,
            AuthorizationPolicy authorization,
            BCryptPasswordEncoder passwords) {
        AdminAccountService service() {
            return new AdminAccountService(
                    accounts, userRoles, roles, studentProfiles, faculties, departments,
                    authorization, passwords);
        }
    }
}

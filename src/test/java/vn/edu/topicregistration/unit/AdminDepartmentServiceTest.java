package vn.edu.topicregistration.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.FacultyRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.service.AdminDepartmentService;
import vn.edu.topicregistration.service.AdminDepartmentService.DepartmentCommand;
import vn.edu.topicregistration.service.AdminDepartmentService.DepartmentFilter;

class AdminDepartmentServiceTest {
    @Test
    void workspaceNormalizesFiltersAndKeepsResultsInsideSelectedFaculty() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        FacultyEntity informationTechnology = faculty(1L, "Khoa Công nghệ Thông tin");
        FacultyEntity mechanics = faculty(2L, "Khoa Cơ khí");
        List<DepartmentEntity> catalog = List.of(
                department(11L, informationTechnology, "Công nghệ phần mềm"),
                department(12L, informationTechnology, "Hệ thống thông tin"),
                department(21L, mechanics, "Công nghệ chế tạo máy"));
        when(dependencies.faculties().findAllByOrderByNameAsc())
                .thenReturn(List.of(mechanics, informationTechnology));
        when(dependencies.departments().findAllDetailed()).thenReturn(catalog);

        var workspace = dependencies.service().workspace(
                new DepartmentFilter("  CÔNG nghệ  ", 1L));

        assertEquals(List.of("Công nghệ phần mềm"), workspace.departments().stream()
                .map(AdminDepartmentService.DepartmentView::name).toList());
        assertEquals("CÔNG nghệ", workspace.filter().query());
        assertEquals(1L, workspace.filter().facultyId());
        assertEquals(3, workspace.totalDepartments());
    }

    @Test
    void createTrimsNameAndAllowsSameNameInAnotherFaculty() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        FacultyEntity faculty = faculty(2L, "Khoa Cơ khí");
        when(dependencies.faculties().findById(2L)).thenReturn(Optional.of(faculty));
        when(dependencies.departments().existsByFacultyIdAndNameIgnoreCase(
                2L, "Công nghệ phần mềm")).thenReturn(false);
        when(dependencies.departments().saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        dependencies.service().create(new DepartmentCommand(2L, "  Công nghệ phần mềm  "));

        ArgumentCaptor<DepartmentEntity> saved = ArgumentCaptor.forClass(DepartmentEntity.class);
        verify(dependencies.departments()).saveAndFlush(saved.capture());
        assertEquals("Công nghệ phần mềm", saved.getValue().getName());
        assertEquals(2L, saved.getValue().getFaculty().getId());
    }

    @Test
    void duplicateCreateAndUpdateAreRejectedWithoutWriting() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        FacultyEntity faculty = faculty(1L, "Khoa Công nghệ Thông tin");
        DepartmentEntity current = department(7L, faculty, "Hệ thống thông tin");
        when(dependencies.faculties().findById(1L)).thenReturn(Optional.of(faculty));
        when(dependencies.departments().existsByFacultyIdAndNameIgnoreCase(
                1L, "Hệ thống thông tin")).thenReturn(true);
        when(dependencies.departments().findByIdAndFacultyId(7L, 1L))
                .thenReturn(Optional.of(current));
        when(dependencies.departments().existsByFacultyIdAndNameIgnoreCaseAndIdNot(
                1L, "Mạng máy tính", 7L)).thenReturn(true);

        ApplicationException createFailure = assertThrows(ApplicationException.class,
                () -> dependencies.service().create(
                        new DepartmentCommand(1L, "Hệ thống thông tin")));
        ApplicationException updateFailure = assertThrows(ApplicationException.class,
                () -> dependencies.service().update(
                        7L, new DepartmentCommand(1L, "Mạng máy tính")));

        assertEquals(ApplicationErrorCode.DEPARTMENT_NAME_EXISTS, createFailure.code());
        assertEquals(ApplicationErrorCode.DEPARTMENT_NAME_EXISTS, updateFailure.code());
        verify(dependencies.departments(), never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsForgedFacultyAndUnknownDepartment() {
        Dependencies dependencies = dependencies();
        allowAdmin(dependencies);
        when(dependencies.departments().findByIdAndFacultyId(7L, 99L))
                .thenReturn(Optional.empty());

        ApplicationException failure = assertThrows(ApplicationException.class,
                () -> dependencies.service().update(
                        7L, new DepartmentCommand(99L, "Tên mới")));

        assertEquals(ApplicationErrorCode.RESOURCE_NOT_FOUND, failure.code());
        verify(dependencies.departments(), never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonAdminBeforeEveryCatalogOperation() {
        Dependencies dependencies = dependencies();
        when(dependencies.authorization().requireRole(RoleCode.ADMIN))
                .thenThrow(new AccessDeniedException("not admin"));

        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().workspace(null));
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().facultyOptions());
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().department(7L));
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().create(null));
        assertThrows(AccessDeniedException.class,
                () -> dependencies.service().update(7L, null));

        verifyNoInteractions(dependencies.departments(), dependencies.faculties());
    }

    private static void allowAdmin(Dependencies dependencies) {
        when(dependencies.authorization().requireRole(RoleCode.ADMIN)).thenReturn(
                new ActorIdentity(1L, Set.of(RoleCode.ADMIN), null));
    }

    private static FacultyEntity faculty(long id, String name) {
        FacultyEntity faculty = mock(FacultyEntity.class);
        when(faculty.getId()).thenReturn(id);
        when(faculty.getName()).thenReturn(name);
        return faculty;
    }

    private static DepartmentEntity department(long id, FacultyEntity faculty, String name) {
        DepartmentEntity department = mock(DepartmentEntity.class);
        when(department.getId()).thenReturn(id);
        when(department.getFaculty()).thenReturn(faculty);
        when(department.getName()).thenReturn(name);
        return department;
    }

    private static Dependencies dependencies() {
        DepartmentRepository departments = mock(DepartmentRepository.class);
        FacultyRepository faculties = mock(FacultyRepository.class);
        AuthorizationPolicy authorization = mock(AuthorizationPolicy.class);
        return new Dependencies(
                departments,
                faculties,
                authorization,
                new AdminDepartmentService(departments, faculties, authorization));
    }

    private record Dependencies(
            DepartmentRepository departments,
            FacultyRepository faculties,
            AuthorizationPolicy authorization,
            AdminDepartmentService service) {
    }
}

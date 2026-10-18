package vn.edu.topicregistration.service;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.FacultyRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;

@Service
@Profile("!test")
public class AdminDepartmentService {
    private final DepartmentRepository departments;
    private final FacultyRepository faculties;
    private final AuthorizationPolicy authorization;

    public AdminDepartmentService(
            DepartmentRepository departments,
            FacultyRepository faculties,
            AuthorizationPolicy authorization) {
        this.departments = departments;
        this.faculties = faculties;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public DepartmentWorkspace workspace(DepartmentFilter filter) {
        authorization.requireRole(RoleCode.ADMIN);
        DepartmentFilter safeFilter = filter == null
                ? DepartmentFilter.empty()
                : filter.normalized();
        List<DepartmentView> all = departments.findAllDetailed().stream()
                .map(AdminDepartmentService::view)
                .toList();
        List<DepartmentView> visible = all.stream()
                .filter(department -> matches(department, safeFilter))
                .toList();
        return new DepartmentWorkspace(
                visible,
                facultyOptionsWithoutAuthorization(),
                safeFilter,
                all.size());
    }

    @Transactional(readOnly = true)
    public List<FacultyOption> facultyOptions() {
        authorization.requireRole(RoleCode.ADMIN);
        return facultyOptionsWithoutAuthorization();
    }

    @Transactional(readOnly = true)
    public DepartmentView department(long departmentId) {
        authorization.requireRole(RoleCode.ADMIN);
        if (departmentId <= 0) {
            throw notFound();
        }
        return departments.findDetailedById(departmentId)
                .map(AdminDepartmentService::view)
                .orElseThrow(AdminDepartmentService::notFound);
    }

    @Transactional
    public DepartmentView create(DepartmentCommand command) {
        authorization.requireRole(RoleCode.ADMIN);
        PreparedDepartment prepared = prepare(command);
        FacultyEntity faculty = faculties.findById(prepared.facultyId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.VALIDATION_FAILED));
        if (departments.existsByFacultyIdAndNameIgnoreCase(
                prepared.facultyId(), prepared.name())) {
            throw duplicateName();
        }
        return view(departments.saveAndFlush(
                new DepartmentEntity(faculty, prepared.name())));
    }

    @Transactional
    public DepartmentView update(long departmentId, DepartmentCommand command) {
        authorization.requireRole(RoleCode.ADMIN);
        if (departmentId <= 0) {
            throw notFound();
        }
        PreparedDepartment prepared = prepare(command);
        DepartmentEntity department = departments.findByIdAndFacultyId(
                        departmentId, prepared.facultyId())
                .orElseThrow(AdminDepartmentService::notFound);
        if (departments.existsByFacultyIdAndNameIgnoreCaseAndIdNot(
                prepared.facultyId(), prepared.name(), departmentId)) {
            throw duplicateName();
        }
        department.rename(prepared.name());
        return view(departments.saveAndFlush(department));
    }

    private List<FacultyOption> facultyOptionsWithoutAuthorization() {
        return faculties.findAllByOrderByNameAsc().stream()
                .map(faculty -> new FacultyOption(faculty.getId(), faculty.getName()))
                .toList();
    }

    private static PreparedDepartment prepare(DepartmentCommand command) {
        if (command == null || command.facultyId() == null || command.facultyId() <= 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        String name = command.name() == null ? "" : command.name().trim();
        if (name.isBlank() || name.length() > 255) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        return new PreparedDepartment(command.facultyId(), name);
    }

    private static boolean matches(DepartmentView department, DepartmentFilter filter) {
        if (filter.facultyId() != null
                && !filter.facultyId().equals(department.facultyId())) {
            return false;
        }
        return filter.query() == null
                || department.name().toLowerCase(Locale.ROOT)
                        .contains(filter.query().toLowerCase(Locale.ROOT));
    }

    private static DepartmentView view(DepartmentEntity department) {
        FacultyEntity faculty = department.getFaculty();
        return new DepartmentView(
                department.getId(),
                department.getName(),
                faculty.getId(),
                faculty.getName());
    }

    private static ApplicationException duplicateName() {
        return new ApplicationException(ApplicationErrorCode.DEPARTMENT_NAME_EXISTS);
    }

    private static ApplicationException notFound() {
        return new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
    }

    private record PreparedDepartment(long facultyId, String name) {
    }

    public record DepartmentCommand(Long facultyId, String name) {
    }

    public record DepartmentFilter(String query, Long facultyId) {
        public static DepartmentFilter empty() {
            return new DepartmentFilter(null, null);
        }

        public DepartmentFilter normalized() {
            String safeQuery = query == null || query.isBlank() ? null : query.trim();
            Long safeFacultyId = facultyId != null && facultyId > 0 ? facultyId : null;
            return new DepartmentFilter(safeQuery, safeFacultyId);
        }
    }

    public record FacultyOption(Long id, String name) {
    }

    public record DepartmentView(
            Long id,
            String name,
            Long facultyId,
            String facultyName) {
    }

    public record DepartmentWorkspace(
            List<DepartmentView> departments,
            List<FacultyOption> faculties,
            DepartmentFilter filter,
            long totalDepartments) {
        public DepartmentWorkspace {
            departments = List.copyOf(departments);
            faculties = List.copyOf(faculties);
        }

        public static DepartmentWorkspace empty() {
            return new DepartmentWorkspace(
                    List.of(), List.of(), DepartmentFilter.empty(), 0);
        }
    }
}

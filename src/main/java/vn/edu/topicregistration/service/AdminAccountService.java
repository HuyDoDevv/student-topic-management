package vn.edu.topicregistration.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

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
import vn.edu.topicregistration.security.PasswordPolicy;

@Service
@Profile("!test")
public class AdminAccountService {
    public static final String DEFAULT_FACULTY_NAME = "Khoa Công nghệ Thông tin";
    private static final Pattern USERNAME = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$");
    private static final Pattern STUDENT_CODE = Pattern.compile("^[0-9]{8}$");
    private static final Pattern CLASS_CODE = Pattern.compile("^[A-Z0-9]+$");

    private final UserAccountRepository accounts;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final StudentProfileRepository studentProfiles;
    private final FacultyRepository faculties;
    private final DepartmentRepository departments;
    private final AuthorizationPolicy authorization;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountService(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            RoleRepository roles,
            StudentProfileRepository studentProfiles,
            FacultyRepository faculties,
            DepartmentRepository departments,
            AuthorizationPolicy authorization,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.userRoles = userRoles;
        this.roles = roles;
        this.studentProfiles = studentProfiles;
        this.faculties = faculties;
        this.departments = departments;
        this.authorization = authorization;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AccountWorkspace workspace(AccountFilter filter) {
        authorization.requireRole(RoleCode.ADMIN);
        AccountFilter safeFilter = filter == null ? AccountFilter.empty() : filter.normalized();
        List<FacultyOption> facultyOptions = faculties.findAllByOrderByNameAsc().stream()
                .map(faculty -> new FacultyOption(faculty.getId(), faculty.getName()))
                .toList();
        List<AccountDepartmentOption> departmentOptions = safeFilter.accountType() == AccountType.STUDENT
                ? List.of()
                : departments.findAllDetailed().stream()
                        .filter(department -> safeFilter.facultyId() == null
                                || safeFilter.facultyId().equals(department.getFaculty().getId()))
                        .map(department -> new AccountDepartmentOption(
                                department.getId(), department.getName(),
                                department.getFaculty().getName()))
                        .toList();
        List<AccountView> all = accounts.findAllDetailed().stream()
                .map(AdminAccountService::accountView)
                .toList();
        List<AccountView> visible = all.stream()
                .filter(account -> matches(account, safeFilter))
                .toList();
        return new AccountWorkspace(
                visible,
                facultyOptions,
                departmentOptions,
                safeFilter,
                all.size(),
                all.stream().filter(AccountView::enabled).count(),
                all.stream().filter(account -> account.accountType() == AccountType.STUDENT).count(),
                all.stream().filter(account -> account.accountType() == AccountType.LECTURER
                        || account.accountType() == AccountType.DEAN).count());
    }

    @Transactional
    public AccountView createStudent(CreateStudentCommand command) {
        authorization.requireRole(RoleCode.ADMIN);
        PreparedStudent prepared = prepareStudent(command);
        ensureAvailable(prepared.account());
        if (studentProfiles.existsByStudentCodeIgnoreCase(prepared.studentCode())) {
            throw new ApplicationException(ApplicationErrorCode.ACCOUNT_STUDENT_CODE_EXISTS);
        }
        FacultyEntity faculty = defaultFaculty();
        Map<RoleCode, RoleEntity> roleMap = requiredRoles(AccountType.STUDENT.roles());
        UserAccountEntity account = persist(prepared.account(), faculty, null, roleMap);
        StudentProfileEntity profile = studentProfiles.saveAndFlush(new StudentProfileEntity(
                account, prepared.studentCode(), prepared.className(), prepared.cohortCode()));
        return accountView(account, AccountType.STUDENT, profile);
    }

    @Transactional
    public AccountView createLecturer(CreateLecturerCommand command) {
        authorization.requireRole(RoleCode.ADMIN);
        return createStaff(AccountType.LECTURER,
                command == null ? null : command.username(),
                command == null ? null : command.fullName(),
                command == null ? null : command.password(),
                command == null ? null : command.departmentId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountView createDean(CreateDeanCommand command) {
        authorization.requireRole(RoleCode.ADMIN);
        return createStaff(AccountType.DEAN,
                command == null ? null : command.username(),
                command == null ? null : command.fullName(),
                command == null ? null : command.password(),
                command == null ? null : command.departmentId());
    }

    @Transactional(readOnly = true)
    public List<DepartmentOption> staffDepartmentOptions() {
        authorization.requireRole(RoleCode.ADMIN);
        return departments.findAllByFacultyIdOrderByNameAsc(defaultFaculty().getId()).stream()
                .map(department -> new DepartmentOption(department.getId(), department.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentOption> staffDepartmentOptionsForAccount(long accountId) {
        authorization.requireRole(RoleCode.ADMIN);
        if (accountId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        UserAccountEntity account = accounts.findDetailedById(accountId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        staffType(account);
        if (account.getFaculty() == null) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        return departments.findAllByFacultyIdOrderByNameAsc(account.getFaculty().getId()).stream()
                .map(department -> new DepartmentOption(department.getId(), department.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffDepartmentTarget staffDepartmentTarget(long accountId) {
        authorization.requireRole(RoleCode.ADMIN);
        if (accountId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        UserAccountEntity account = accounts.findDetailedById(accountId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        AccountType type = staffType(account);
        FacultyEntity faculty = account.getFaculty();
        if (faculty == null) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        return new StaffDepartmentTarget(
                account.getId(), account.getUsername(), account.getFullName(), type,
                faculty.getName(),
                account.getDepartment() == null ? null : account.getDepartment().getId());
    }

    @Transactional
    public StaffDepartmentTarget assignStaffDepartment(long accountId, Long departmentId) {
        authorization.requireRole(RoleCode.ADMIN);
        if (accountId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        if (departmentId == null || departmentId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        UserAccountEntity account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        AccountType type = staffType(account);
        FacultyEntity faculty = account.getFaculty();
        if (faculty == null) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        DepartmentEntity existing = account.getDepartment();
        if (existing != null && !departmentId.equals(existing.getId())) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        DepartmentEntity department = departments.findByIdAndFacultyId(departmentId, faculty.getId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED));
        if (existing == null) {
            account.assignDepartment(department);
            accounts.flush();
        }
        return new StaffDepartmentTarget(
                account.getId(), account.getUsername(), account.getFullName(), type,
                faculty.getName(), department.getId());
    }

    @Transactional(readOnly = true)
    public PasswordResetTarget passwordResetTarget(long accountId) {
        authorization.requireRole(RoleCode.ADMIN);
        UserAccountEntity account = accounts.findDetailedById(accountId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        AccountType type = managedAccountType(account);
        return new PasswordResetTarget(
                account.getId(),
                account.getUsername(),
                account.getFullName() == null || account.getFullName().isBlank()
                        ? account.getUsername()
                        : account.getFullName(),
                type,
                type.label(),
                account.isEnabled());
    }

    @Transactional
    public PasswordResetResult resetPassword(ResetPasswordCommand command) {
        authorization.requireRole(RoleCode.ADMIN);
        Objects.requireNonNull(command, "command");
        if (command.accountId() <= 0) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!PasswordPolicy.isValid(command.newPassword())
                || !Objects.equals(command.newPassword(), command.confirmPassword())) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }

        UserAccountEntity account = accounts.findByIdForUpdate(command.accountId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        AccountType type = managedAccountType(account);
        if (passwordEncoder.matches(command.newPassword(), account.getPasswordHash())) {
            throw new ApplicationException(ApplicationErrorCode.ACCOUNT_PASSWORD_REUSED);
        }

        account.resetPassword(passwordEncoder.encode(command.newPassword()));
        accounts.flush();
        return new PasswordResetResult(account.getId(), account.getUsername(), type);
    }

    @Transactional
    public AccountStatusResult lockAccount(long accountId) {
        return changeAccountStatus(accountId, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountStatusResult unlockAccount(long accountId) {
        authorization.requireRole(RoleCode.ADMIN);
        if (accountId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        accounts.findFacultyIdByAccountIdAndRole(accountId, RoleCode.DEAN)
                .ifPresent(this::lockFaculty);
        UserAccountEntity account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        AccountType type = managedAccountType(account);
        if (type == AccountType.DEAN) {
            FacultyEntity faculty = account.getFaculty();
            if (faculty == null) {
                throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
            }
            ensureDeanSlotAvailable(faculty.getId(), accountId);
        }
        boolean changed = account.enable();
        if (changed) {
            accounts.flush();
        }
        return new AccountStatusResult(
                account.getId(), account.getUsername(), type, true, changed);
    }

    private AccountStatusResult changeAccountStatus(long accountId, boolean enabled) {
        authorization.requireRole(RoleCode.ADMIN);
        if (accountId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        UserAccountEntity account = accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        AccountType type = managedAccountType(account);
        boolean changed = enabled ? account.enable() : account.disable();
        if (changed) {
            accounts.flush();
        }
        return new AccountStatusResult(
                account.getId(), account.getUsername(), type, enabled, changed);
    }

    private static AccountType staffType(UserAccountEntity account) {
        AccountType type = managedAccountType(account);
        if (type != AccountType.LECTURER && type != AccountType.DEAN) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        return type;
    }

    private AccountView createStaff(
            AccountType type, String username, String fullName, String password, Long departmentId) {
        PreparedAccount prepared = prepareAccount(type, username, fullName, password, null);
        ensureAvailable(prepared);
        FacultyEntity faculty = defaultFaculty();
        DepartmentEntity department = staffDepartment(departmentId, faculty);
        if (type == AccountType.DEAN) {
            lockFaculty(faculty.getId());
            ensureDeanSlotAvailable(faculty.getId(), null);
        }
        Map<RoleCode, RoleEntity> roleMap = requiredRoles(type.roles());
        return accountView(persist(prepared, faculty, department, roleMap), type);
    }

    private void lockFaculty(long facultyId) {
        faculties.findByIdForUpdate(facultyId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED));
    }

    private void ensureDeanSlotAvailable(long facultyId, Long excludedAccountId) {
        boolean occupied = excludedAccountId == null
                ? accounts.existsEnabledByFacultyAndRole(facultyId, RoleCode.DEAN)
                : accounts.existsEnabledByFacultyAndRoleExcept(
                        facultyId, RoleCode.DEAN, excludedAccountId);
        if (occupied) {
            throw new ApplicationException(ApplicationErrorCode.DEAN_ALREADY_ACTIVE);
        }
    }

    private DepartmentEntity staffDepartment(Long departmentId, FacultyEntity faculty) {
        if (departmentId == null || departmentId <= 0) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        return departments.findByIdAndFacultyId(departmentId, faculty.getId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED));
    }

    private FacultyEntity defaultFaculty() {
        return faculties.findByNameIgnoreCase(DEFAULT_FACULTY_NAME)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.VALIDATION_FAILED));
    }

    private void ensureAvailable(PreparedAccount prepared) {
        if (accounts.existsByUsernameIgnoreCase(prepared.username())) {
            throw new ApplicationException(ApplicationErrorCode.ACCOUNT_USERNAME_EXISTS);
        }
        if (prepared.email() != null && accounts.existsByEmailIgnoreCase(prepared.email())) {
            throw new ApplicationException(ApplicationErrorCode.ACCOUNT_EMAIL_EXISTS);
        }
    }

    private UserAccountEntity persist(
            PreparedAccount prepared,
            FacultyEntity faculty,
            DepartmentEntity department,
            Map<RoleCode, RoleEntity> roleMap) {
        UserAccountEntity account = new UserAccountEntity(
                prepared.fullName(),
                prepared.username(),
                passwordEncoder.encode(prepared.password()),
                prepared.email(),
                faculty, department);
        account.markPasswordAsTemporary();
        UserAccountEntity saved = accounts.saveAndFlush(account);
        List<UserRoleEntity> assignments = prepared.accountType().roles().stream()
                .map(code -> new UserRoleEntity(saved, roleMap.get(code)))
                .toList();
        userRoles.saveAllAndFlush(assignments);
        return saved;
    }

    private Map<RoleCode, RoleEntity> requiredRoles(Set<RoleCode> codes) {
        Map<RoleCode, RoleEntity> result = new EnumMap<>(RoleCode.class);
        roles.findAllById(codes).forEach(role -> result.put(role.getCode(), role));
        if (!result.keySet().containsAll(codes)) {
            throw new IllegalStateException("Required account roles are missing");
        }
        return result;
    }

    private static AccountType managedAccountType(UserAccountEntity account) {
        AccountType type = AccountType.fromRoles(account.getRoleCodes());
        if (type == null || !type.creatable()) {
            throw new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND);
        }
        return type;
    }

    private static PreparedStudent prepareStudent(CreateStudentCommand command) {
        Objects.requireNonNull(command, "command");
        String studentCode = trim(command.studentCode());
        if (!STUDENT_CODE.matcher(studentCode).matches()) {
            throw new IllegalArgumentException("MSSV phải gồm đúng 8 chữ số.");
        }
        String className = trim(command.className()).toUpperCase(Locale.ROOT);
        if (className.length() > 64 || !CLASS_CODE.matcher(className).matches()) {
            throw new IllegalArgumentException(
                    "Lớp là bắt buộc, chỉ gồm chữ không dấu và số, tối đa 64 ký tự.");
        }
        String cohortCode = trim(command.cohortCode()).toUpperCase(Locale.ROOT);
        if (!cohortCode.matches("^K[0-9]{4}$")) {
            throw new IllegalArgumentException("Khóa phải có định dạng K2026.");
        }
        String email = studentCode.toLowerCase(Locale.ROOT) + "@student.hcmute.edu.vn";
        PreparedAccount account = prepareAccount(
                AccountType.STUDENT, studentCode, command.fullName(), command.password(), email);
        return new PreparedStudent(account, studentCode, className, cohortCode);
    }

    private static PreparedAccount prepareAccount(
            AccountType type, String rawUsername, String rawFullName,
            String rawPassword, String email) {
        if (type == null || !type.creatable()) {
            throw new IllegalArgumentException("Loại tài khoản không thể được tạo.");
        }
        String username = trim(rawUsername);
        String fullName = trim(rawFullName);
        String password = rawPassword == null ? "" : rawPassword;
        if (username.isEmpty() || username.length() > 191 || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Tên đăng nhập chỉ gồm chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang và tối đa 191 ký tự.");
        }
        if (fullName.isEmpty() || fullName.length() > 255) {
            throw new IllegalArgumentException("Họ tên là bắt buộc và tối đa 255 ký tự.");
        }
        if (!PasswordPolicy.isValid(password)) {
            throw new IllegalArgumentException(
                    "Mật khẩu tạm thời phải có ít nhất 8 ký tự và không quá 72 byte UTF-8.");
        }
        return new PreparedAccount(type, username, fullName, password, email);
    }

    private static boolean matches(AccountView account, AccountFilter filter) {
        if (filter.accountType() != null && account.accountType() != filter.accountType()) {
            return false;
        }
        if (filter.facultyId() != null && !filter.facultyId().equals(account.facultyId())) {
            return false;
        }
        if (filter.departmentId() != null
                && !filter.departmentId().equals(account.departmentId())) {
            return false;
        }
        if (filter.enabled() != null && filter.enabled() != account.enabled()) {
            return false;
        }
        if (filter.query() == null || filter.query().isBlank()) {
            return true;
        }
        String query = filter.query().toLowerCase(Locale.forLanguageTag("vi-VN"));
        return account.username().toLowerCase(Locale.ROOT).contains(query)
                || account.fullName().toLowerCase(Locale.forLanguageTag("vi-VN")).contains(query);
    }

    private static AccountView accountView(UserAccountEntity account) {
        AccountType type = AccountType.fromRoles(account.getRoleCodes());
        return accountView(account, type);
    }

    private static AccountView accountView(UserAccountEntity account, AccountType type) {
        return accountView(account, type, account.getStudentProfile());
    }

    private static AccountView accountView(
            UserAccountEntity account, AccountType type, StudentProfileEntity profile) {
        FacultyEntity faculty = account.getFaculty();
        DepartmentEntity department = account.getDepartment();
        return new AccountView(
                account.getId(),
                account.getUsername(),
                account.getFullName() == null || account.getFullName().isBlank()
                        ? account.getUsername()
                        : account.getFullName(),
                type,
                type == null ? "Chưa xác định" : type.label(),
                faculty == null ? null : faculty.getId(),
                faculty == null ? "Không áp dụng" : faculty.getName(),
                department == null ? null : department.getId(),
                department == null ? null : department.getName(),
                account.getEmail(),
                profile == null ? null : profile.getStudentCode(),
                profile == null ? null : profile.getClassName(),
                profile == null ? null : profile.getCohortCode(),
                account.isEnabled());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public enum AccountType {
        ADMIN("Quản trị viên", false, Set.of(RoleCode.ADMIN)),
        STUDENT("Sinh viên", true, Set.of(RoleCode.STUDENT)),
        LECTURER("Giảng viên", true, Set.of(RoleCode.LECTURER)),
        DEAN("Trưởng khoa", true, Set.of(RoleCode.DEAN));

        private final String label;
        private final boolean creatable;
        private final Set<RoleCode> roles;

        AccountType(String label, boolean creatable, Set<RoleCode> roles) {
            this.label = label;
            this.creatable = creatable;
            this.roles = Set.copyOf(roles);
        }

        public String label() {
            return label;
        }

        public boolean creatable() {
            return creatable;
        }

        public Set<RoleCode> roles() {
            return roles;
        }

        static AccountType fromRoles(Set<RoleCode> roles) {
            if (roles.contains(RoleCode.ADMIN)) {
                return ADMIN;
            }
            if (roles.contains(RoleCode.DEAN)) {
                return DEAN;
            }
            if (roles.contains(RoleCode.LECTURER)) {
                return LECTURER;
            }
            return roles.contains(RoleCode.STUDENT) ? STUDENT : null;
        }
    }

    public record CreateStudentCommand(
            String studentCode,
            String fullName,
            String className,
            String cohortCode,
            String password) {
    }

    public record CreateLecturerCommand(
            String username, String fullName, String password, Long departmentId) {
    }

    public record CreateDeanCommand(
            String username, String fullName, String password, Long departmentId) {
    }

    public record DepartmentOption(long id, String name) {
    }

    public record StaffDepartmentTarget(
            long id, String username, String fullName, AccountType accountType,
            String facultyName, Long departmentId) {
    }

    public record ResetPasswordCommand(
            long accountId,
            String newPassword,
            String confirmPassword) {
    }

    public record PasswordResetTarget(
            long id,
            String username,
            String fullName,
            AccountType accountType,
            String accountTypeLabel,
            boolean enabled) {
    }

    public record PasswordResetResult(
            long id,
            String username,
            AccountType accountType) {
    }

    public record AccountStatusResult(
            long id,
            String username,
            AccountType accountType,
            boolean enabled,
            boolean changed) {
    }

    public record AccountFilter(
            String query,
            AccountType accountType,
            Long facultyId,
            Long departmentId,
            Boolean enabled) {
        public static AccountFilter empty() {
            return new AccountFilter(null, null, null, null, null);
        }

        AccountFilter normalized() {
            return new AccountFilter(trim(query), accountType, positive(facultyId),
                    accountType == AccountType.STUDENT ? null : positive(departmentId), enabled);
        }

        private static Long positive(Long value) {
            return value == null || value <= 0 ? null : value;
        }
    }

    public record AccountView(
            Long id,
            String username,
            String fullName,
            AccountType accountType,
            String accountTypeLabel,
            Long facultyId,
            String facultyName,
            Long departmentId,
            String departmentName,
            String email,
            String studentCode,
            String className,
            String cohortCode,
            boolean enabled) {
    }

    public record FacultyOption(long id, String name) {
    }

    public record AccountDepartmentOption(long id, String name, String facultyName) {
    }

    public record AccountWorkspace(
            List<AccountView> accounts,
            List<FacultyOption> faculties,
            List<AccountDepartmentOption> departments,
            AccountFilter filter,
            long totalAccounts,
            long enabledAccounts,
            long studentAccounts,
            long staffAccounts) {
        public AccountWorkspace {
            accounts = List.copyOf(accounts);
            faculties = List.copyOf(faculties);
            departments = List.copyOf(departments);
        }

        public static AccountWorkspace empty() {
            return new AccountWorkspace(
                    List.of(), List.of(), List.of(), AccountFilter.empty(),
                    0, 0, 0, 0);
        }
    }

    private record PreparedAccount(
            AccountType accountType,
            String username,
            String fullName,
            String password,
            String email) {
    }

    private record PreparedStudent(
            PreparedAccount account,
            String studentCode,
            String className,
            String cohortCode) {
    }
}

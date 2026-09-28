package vn.edu.topicregistration.service;

import java.nio.charset.StandardCharsets;
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

import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.RoleEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserRoleEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.DepartmentRepository;
import vn.edu.topicregistration.repository.FacultyRepository;
import vn.edu.topicregistration.repository.RoleRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserRoleRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;

@Service
@Profile("!test")
public class AdminAccountService {
    private static final List<String> CSV_HEADER = List.of(
            "account_type", "username", "full_name", "password", "department_id");
    private static final int MAX_IMPORT_BYTES = 1_048_576;
    private static final int MAX_IMPORT_ROWS = 5_000;
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final UserAccountRepository accounts;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final DepartmentRepository departments;
    private final FacultyRepository faculties;
    private final AuthorizationPolicy authorization;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountService(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            RoleRepository roles,
            DepartmentRepository departments,
            FacultyRepository faculties,
            AuthorizationPolicy authorization,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.userRoles = userRoles;
        this.roles = roles;
        this.departments = departments;
        this.faculties = faculties;
        this.authorization = authorization;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AccountWorkspace workspace(AccountFilter filter) {
        authorization.requireRole(RoleCode.ADMIN);
        AccountFilter safeFilter = filter == null ? AccountFilter.empty() : filter.normalized();
        List<DepartmentOption> departmentOptions = departments
                .findAllByOrderByFacultyNameAscNameAsc().stream()
                .map(AdminAccountService::departmentOption)
                .toList();
        List<FacultyOption> facultyOptions = faculties.findAllByOrderByNameAsc().stream()
                .map(faculty -> new FacultyOption(faculty.getId(), faculty.getName()))
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
                creatableTypes(),
                safeFilter,
                all.size(),
                all.stream().filter(AccountView::enabled).count(),
                all.stream().filter(account -> account.accountType() == AccountType.STUDENT).count(),
                all.stream().filter(account -> account.accountType() == AccountType.LECTURER
                        || account.accountType() == AccountType.DEAN).count());
    }

    @Transactional
    public AccountView create(CreateAccountCommand command) {
        authorization.requireRole(RoleCode.ADMIN);
        PreparedAccount prepared = prepare(command, 0);
        if (accounts.existsByUsernameIgnoreCase(prepared.username())) {
            throw new ApplicationException(ApplicationErrorCode.ACCOUNT_USERNAME_EXISTS);
        }
        DepartmentEntity department = departments.findById(prepared.departmentId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.VALIDATION_FAILED));
        Map<RoleCode, RoleEntity> roleMap = requiredRoles(prepared.accountType().roles());
        return accountView(
                persist(prepared, department, roleMap),
                prepared.accountType());
    }

    private UserAccountEntity persist(
            PreparedAccount prepared,
            DepartmentEntity department,
            Map<RoleCode, RoleEntity> roleMap) {
        UserAccountEntity account = new UserAccountEntity(
                prepared.fullName(),
                prepared.username(),
                passwordEncoder.encode(prepared.password()),
                department);
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

    private static PreparedAccount prepare(
            CreateAccountCommand command,
            int row) {
        Objects.requireNonNull(command, "command");
        if (command.accountType() == null || !command.accountType().creatable()) {
            throw new IllegalArgumentException(
                    "Loại tài khoản chỉ nhận STUDENT, LECTURER hoặc DEAN.");
        }
        String username = trim(command.username());
        String fullName = trim(command.fullName());
        String password = command.password() == null ? "" : command.password();
        if (username.isEmpty() || username.length() > 191 || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Tên đăng nhập chỉ gồm chữ không dấu, số, dấu chấm, gạch dưới hoặc gạch ngang và tối đa 191 ký tự.");
        }
        if (fullName.isEmpty() || fullName.length() > 255) {
            throw new IllegalArgumentException("Họ tên là bắt buộc và tối đa 255 ký tự.");
        }
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.isBlank() || password.length() < 8 || passwordBytes > 72) {
            throw new IllegalArgumentException(
                    "Mật khẩu tạm thời phải có ít nhất 8 ký tự và không quá 72 byte UTF-8.");
        }
        if (command.departmentId() == null || command.departmentId() <= 0) {
            throw new IllegalArgumentException("Mã bộ môn phải là số nguyên dương.");
        }
        return new PreparedAccount(
                row, command.accountType(), username, fullName, password, command.departmentId());
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
        DepartmentEntity department = account.getDepartment();
        return new AccountView(
                account.getId(),
                account.getUsername(),
                account.getFullName() == null || account.getFullName().isBlank()
                        ? account.getUsername()
                        : account.getFullName(),
                type,
                type == null ? "Chưa xác định" : type.label(),
                department == null ? null : department.getId(),
                department == null ? "Chưa gán" : department.getName(),
                department == null || department.getFaculty() == null
                        ? null
                        : department.getFaculty().getId(),
                department == null || department.getFaculty() == null
                        ? "Chưa gán"
                        : department.getFaculty().getName(),
                account.isEnabled());
    }

    private static AccountType parseAccountType(String value) {
        try {
            AccountType type = AccountType.valueOf(trim(value).toUpperCase(Locale.ROOT));
            if (!type.creatable()) {
                throw new IllegalArgumentException();
            }
            return type;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Loại tài khoản chỉ nhận STUDENT, LECTURER hoặc DEAN.", exception);
        }
    }

    private static long parseDepartmentId(String value) {
        try {
            long departmentId = Long.parseLong(trim(value));
            if (departmentId <= 0) {
                throw new NumberFormatException();
            }
            return departmentId;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Mã bộ môn phải là số nguyên dương.", exception);
        }
    }

    private static DepartmentOption departmentOption(DepartmentEntity department) {
        return new DepartmentOption(
                department.getId(),
                department.getName(),
                department.getFaculty().getId(),
                department.getFaculty().getName());
    }

    private static List<AccountType> creatableTypes() {
        return List.of(AccountType.STUDENT, AccountType.LECTURER, AccountType.DEAN);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    public enum AccountType {
        ADMIN("Quản trị viên", false, Set.of(RoleCode.ADMIN)),
        STUDENT("Sinh viên", true, Set.of(RoleCode.STUDENT)),
        LECTURER("Giảng viên", true, Set.of(RoleCode.LECTURER)),
        DEAN("Trưởng khoa", true, Set.of(RoleCode.DEAN, RoleCode.LECTURER));

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

    public record CreateAccountCommand(
            AccountType accountType,
            String username,
            String fullName,
            String password,
            Long departmentId) {
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
                    positive(departmentId), enabled);
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
            Long departmentId,
            String departmentName,
            Long facultyId,
            String facultyName,
            boolean enabled) {
    }

    public record FacultyOption(long id, String name) {
    }

    public record DepartmentOption(long id, String name, long facultyId, String facultyName) {
    }

    public record AccountWorkspace(
            List<AccountView> accounts,
            List<FacultyOption> faculties,
            List<DepartmentOption> departments,
            List<AccountType> creatableTypes,
            AccountFilter filter,
            long totalAccounts,
            long enabledAccounts,
            long studentAccounts,
            long staffAccounts) {
        public AccountWorkspace {
            accounts = List.copyOf(accounts);
            faculties = List.copyOf(faculties);
            departments = List.copyOf(departments);
            creatableTypes = List.copyOf(creatableTypes);
        }

        public static AccountWorkspace empty() {
            return new AccountWorkspace(
                    List.of(), List.of(), List.of(), AdminAccountService.creatableTypes(),
                    AccountFilter.empty(),
                    0, 0, 0, 0);
        }
    }

    private record PreparedAccount(
            int row,
            AccountType accountType,
            String username,
            String fullName,
            String password,
            long departmentId) {
    }

    private record CsvRecord(int row, List<String> fields) {
    }
}

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

import vn.edu.topicregistration.entity.FacultyEntity;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.RoleEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserRoleEntity;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.repository.FacultyRepository;
import vn.edu.topicregistration.repository.RoleRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserRoleRepository;
import vn.edu.topicregistration.security.AuthorizationPolicy;

@Service
@Profile("!test")
public class AdminAccountService {
    public static final String DEFAULT_FACULTY_NAME = "Khoa Công nghệ Thông tin";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final UserAccountRepository accounts;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final FacultyRepository faculties;
    private final AuthorizationPolicy authorization;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountService(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            RoleRepository roles,
            FacultyRepository faculties,
            AuthorizationPolicy authorization,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.userRoles = userRoles;
        this.roles = roles;
        this.faculties = faculties;
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
        List<AccountView> all = accounts.findAllDetailed().stream()
                .map(AdminAccountService::accountView)
                .toList();
        List<AccountView> visible = all.stream()
                .filter(account -> matches(account, safeFilter))
                .toList();
        return new AccountWorkspace(
                visible,
                facultyOptions,
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
        PreparedAccount prepared = prepare(command);
        if (accounts.existsByUsernameIgnoreCase(prepared.username())) {
            throw new ApplicationException(ApplicationErrorCode.ACCOUNT_USERNAME_EXISTS);
        }
        FacultyEntity faculty = faculties.findByNameIgnoreCase(DEFAULT_FACULTY_NAME)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.VALIDATION_FAILED));
        Map<RoleCode, RoleEntity> roleMap = requiredRoles(prepared.accountType().roles());
        return accountView(
                persist(prepared, faculty, roleMap),
                prepared.accountType());
    }

    private UserAccountEntity persist(
            PreparedAccount prepared,
            FacultyEntity faculty,
            Map<RoleCode, RoleEntity> roleMap) {
        UserAccountEntity account = new UserAccountEntity(
                prepared.fullName(),
                prepared.username(),
                passwordEncoder.encode(prepared.password()),
                faculty);
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

    private static PreparedAccount prepare(CreateAccountCommand command) {
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
        return new PreparedAccount(command.accountType(), username, fullName, password);
    }

    private static boolean matches(AccountView account, AccountFilter filter) {
        if (filter.accountType() != null && account.accountType() != filter.accountType()) {
            return false;
        }
        if (filter.facultyId() != null && !filter.facultyId().equals(account.facultyId())) {
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
        FacultyEntity faculty = account.getFaculty();
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
                account.isEnabled());
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
            String password) {
    }

    public record AccountFilter(
            String query,
            AccountType accountType,
            Long facultyId,
            Boolean enabled) {
        public static AccountFilter empty() {
            return new AccountFilter(null, null, null, null);
        }

        AccountFilter normalized() {
            return new AccountFilter(trim(query), accountType, positive(facultyId), enabled);
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
            boolean enabled) {
    }

    public record FacultyOption(long id, String name) {
    }

    public record AccountWorkspace(
            List<AccountView> accounts,
            List<FacultyOption> faculties,
            List<AccountType> creatableTypes,
            AccountFilter filter,
            long totalAccounts,
            long enabledAccounts,
            long studentAccounts,
            long staffAccounts) {
        public AccountWorkspace {
            accounts = List.copyOf(accounts);
            faculties = List.copyOf(faculties);
            creatableTypes = List.copyOf(creatableTypes);
        }

        public static AccountWorkspace empty() {
            return new AccountWorkspace(
                    List.of(), List.of(), AdminAccountService.creatableTypes(),
                    AccountFilter.empty(),
                    0, 0, 0, 0);
        }
    }

    private record PreparedAccount(
            AccountType accountType,
            String username,
            String fullName,
            String password) {
    }
}

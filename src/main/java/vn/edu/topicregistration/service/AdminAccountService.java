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

@Service
@Profile("!test")
public class AdminAccountService {
    public static final String DEFAULT_FACULTY_NAME = "Khoa Công nghệ Thông tin";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]+$");

    private final UserAccountRepository accounts;
    private final UserRoleRepository userRoles;
    private final RoleRepository roles;
    private final StudentProfileRepository studentProfiles;
    private final FacultyRepository faculties;
    private final AuthorizationPolicy authorization;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountService(
            UserAccountRepository accounts,
            UserRoleRepository userRoles,
            RoleRepository roles,
            StudentProfileRepository studentProfiles,
            FacultyRepository faculties,
            AuthorizationPolicy authorization,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.userRoles = userRoles;
        this.roles = roles;
        this.studentProfiles = studentProfiles;
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
        UserAccountEntity account = persist(prepared.account(), faculty, roleMap);
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
                command == null ? null : command.password());
    }

    @Transactional
    public AccountView createDean(CreateDeanCommand command) {
        authorization.requireRole(RoleCode.ADMIN);
        return createStaff(AccountType.DEAN,
                command == null ? null : command.username(),
                command == null ? null : command.fullName(),
                command == null ? null : command.password());
    }

    private AccountView createStaff(
            AccountType type, String username, String fullName, String password) {
        PreparedAccount prepared = prepareAccount(type, username, fullName, password, null);
        ensureAvailable(prepared);
        FacultyEntity faculty = defaultFaculty();
        Map<RoleCode, RoleEntity> roleMap = requiredRoles(type.roles());
        return accountView(persist(prepared, faculty, roleMap), type);
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
            Map<RoleCode, RoleEntity> roleMap) {
        UserAccountEntity account = new UserAccountEntity(
                prepared.fullName(),
                prepared.username(),
                passwordEncoder.encode(prepared.password()),
                prepared.email(),
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

    private static PreparedStudent prepareStudent(CreateStudentCommand command) {
        Objects.requireNonNull(command, "command");
        String studentCode = trim(command.studentCode()).toUpperCase(Locale.ROOT);
        if (studentCode.isEmpty() || studentCode.length() > 32
                || !USERNAME.matcher(studentCode).matches()
                || studentCode.contains(".") || studentCode.contains("_")
                || studentCode.contains("-")) {
            throw new IllegalArgumentException("MSSV chỉ gồm chữ không dấu và số, tối đa 32 ký tự.");
        }
        String className = trim(command.className());
        if (className.isEmpty() || className.length() > 64) {
            throw new IllegalArgumentException("Lớp là bắt buộc và tối đa 64 ký tự.");
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
        int passwordBytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.isBlank() || password.length() < 8 || passwordBytes > 72) {
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

    public record CreateStudentCommand(
            String studentCode,
            String fullName,
            String className,
            String cohortCode,
            String password) {
    }

    public record CreateLecturerCommand(String username, String fullName, String password) {
    }

    public record CreateDeanCommand(String username, String fullName, String password) {
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
            String email,
            String studentCode,
            String className,
            String cohortCode,
            boolean enabled) {
    }

    public record FacultyOption(long id, String name) {
    }

    public record AccountWorkspace(
            List<AccountView> accounts,
            List<FacultyOption> faculties,
            AccountFilter filter,
            long totalAccounts,
            long enabledAccounts,
            long studentAccounts,
            long staffAccounts) {
        public AccountWorkspace {
            accounts = List.copyOf(accounts);
            faculties = List.copyOf(faculties);
        }

        public static AccountWorkspace empty() {
            return new AccountWorkspace(
                    List.of(), List.of(), AccountFilter.empty(),
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

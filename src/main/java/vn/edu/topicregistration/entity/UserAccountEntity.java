package vn.edu.topicregistration.entity;


import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_accounts")
public class UserAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(length = 254, unique = true)
    private String email;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, unique = true, length = 191)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "security_version", nullable = false)
    private long securityVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private FacultyEntity faculty;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserRoleEntity> roles = new LinkedHashSet<>();

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private StudentProfileEntity studentProfile;

    protected UserAccountEntity() {
    }

    public UserAccountEntity(
            String fullName,
            String username,
            String passwordHash,
            FacultyEntity faculty) {
        this(fullName, username, passwordHash, null, faculty);
    }

    public UserAccountEntity(
            String fullName,
            String username,
            String passwordHash,
            String email,
            FacultyEntity faculty) {
        this.fullName = fullName;
        this.enabled = true;
        this.username = username;
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        this.securityVersion = 0;
        this.email = email;
        this.faculty = faculty;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public long getSecurityVersion() {
        return securityVersion;
    }

    public FacultyEntity getFaculty() {
        return faculty;
    }

    public StudentProfileEntity getStudentProfile() {
        return studentProfile;
    }

    public Set<RoleCode> getRoleCodes() {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptySet();
        }
        return roles.stream()
                .map(UserRoleEntity::getRole)
                .map(RoleEntity::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void disable() {
        this.enabled = false;
    }

    public void markPasswordAsTemporary() {
        this.mustChangePassword = true;
    }

    public void changePassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("Encoded password is required");
        }
        this.passwordHash = encodedPassword;
        this.mustChangePassword = false;
        this.securityVersion = Math.addExact(this.securityVersion, 1);
    }
}

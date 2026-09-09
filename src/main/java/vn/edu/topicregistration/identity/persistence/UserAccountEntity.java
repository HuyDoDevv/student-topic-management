package vn.edu.topicregistration.identity.persistence;

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
import jakarta.persistence.Table;
import vn.edu.topicregistration.identity.domain.RoleCode;

@Entity
@Table(name = "user_accounts")
public class UserAccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false, unique = true, length = 191)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private DepartmentEntity department;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<UserRoleEntity> roles = new LinkedHashSet<>();

    protected UserAccountEntity() {
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

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public DepartmentEntity getDepartment() {
        return department;
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
}

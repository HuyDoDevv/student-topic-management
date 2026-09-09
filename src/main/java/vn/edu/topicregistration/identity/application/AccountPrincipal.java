package vn.edu.topicregistration.identity.application;

import java.io.Serial;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import vn.edu.topicregistration.identity.domain.RoleCode;

public final class AccountPrincipal implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long accountId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final boolean enabled;
    private final Set<RoleCode> roles;
    private final Long departmentId;
    private final Long facultyId;

    public AccountPrincipal(Long accountId, String username, String passwordHash, String fullName,
            boolean enabled, Set<RoleCode> roles, Long departmentId, Long facultyId) {
        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.enabled = enabled;
        this.roles = Set.copyOf(roles);
        this.departmentId = departmentId;
        this.facultyId = facultyId;
    }

    public Long accountId() {
        return accountId;
    }

    public String fullName() {
        return fullName;
    }

    public Set<RoleCode> roles() {
        return roles;
    }

    public Long departmentId() {
        return departmentId;
    }

    public Long facultyId() {
        return facultyId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .sorted(Comparator.comparing(Enum::name))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}

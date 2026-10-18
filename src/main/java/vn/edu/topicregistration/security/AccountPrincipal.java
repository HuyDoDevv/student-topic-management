package vn.edu.topicregistration.security;

import vn.edu.topicregistration.entity.RoleCode;

import java.io.Serial;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


public final class AccountPrincipal implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Long accountId;
    private final String username;
    private final String passwordHash;
    private final String fullName;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final long securityVersion;
    private final Set<RoleCode> roles;
    private final Long facultyId;

    public AccountPrincipal(Long accountId, String username, String passwordHash, String fullName,
            boolean enabled, Set<RoleCode> roles, Long facultyId) {
        this(accountId, username, passwordHash, fullName, enabled, false, 0, roles, facultyId);
    }

    public AccountPrincipal(Long accountId, String username, String passwordHash, String fullName,
            boolean enabled, boolean mustChangePassword, long securityVersion,
            Set<RoleCode> roles, Long facultyId) {
        this.accountId = accountId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
        this.securityVersion = securityVersion;
        this.roles = Set.copyOf(roles);
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

    public Long facultyId() {
        return facultyId;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public long securityVersion() {
        return securityVersion;
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

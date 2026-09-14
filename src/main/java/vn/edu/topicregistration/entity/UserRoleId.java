package vn.edu.topicregistration.entity;


import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public class UserRoleId implements Serializable {
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", length = 32)
    private RoleCode roleCode;

    protected UserRoleId() {
    }

    public UserRoleId(Long userId, RoleCode roleCode) {
        this.userId = userId;
        this.roleCode = roleCode;
    }

    public Long getUserId() {
        return userId;
    }

    public RoleCode getRoleCode() {
        return roleCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UserRoleId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && roleCode == that.roleCode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleCode);
    }
}

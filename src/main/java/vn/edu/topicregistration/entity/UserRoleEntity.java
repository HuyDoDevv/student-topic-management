package vn.edu.topicregistration.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_roles")
public class UserRoleEntity {
    @EmbeddedId
    private UserRoleId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("roleCode")
    @JoinColumn(name = "role_code", nullable = false)
    private RoleEntity role;

    protected UserRoleEntity() {
    }

    public UserRoleId getId() {
        return id;
    }

    public RoleEntity getRole() {
        return role;
    }
}

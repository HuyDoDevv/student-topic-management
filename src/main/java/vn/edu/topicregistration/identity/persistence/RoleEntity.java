package vn.edu.topicregistration.identity.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import vn.edu.topicregistration.identity.domain.RoleCode;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RoleCode code;

    @Column(nullable = false, length = 100)
    private String name;

    protected RoleEntity() {
    }

    public RoleCode getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}

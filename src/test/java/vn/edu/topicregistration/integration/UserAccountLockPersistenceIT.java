package vn.edu.topicregistration.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.entity.UserRoleEntity;
import vn.edu.topicregistration.repository.RoleRepository;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.repository.UserRoleRepository;

/** Verifies account row locking without Hibernate follow-on locking failures. */
@SpringBootTest
@ActiveProfiles("local")
class UserAccountLockPersistenceIT {
    private static final MySqlTestSupport DATABASE = MySqlTestSupport.fromEnvironment();

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    UserRoleRepository userRoles;

    @Autowired
    RoleRepository roles;

    @Autowired
    EntityManager entityManager;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.springApplicationProperties()
                .forEach((name, value) -> registry.add(name, () -> value));
    }

    @BeforeAll
    static void migrateIsolatedDatabase() {
        DATABASE.resetAndMigrate();
    }

    @Test
    @Transactional
    void repositoryLocksAccountLoadsRolesAndFlushesStatusChange() {
        UserAccountEntity saved = accounts.saveAndFlush(new UserAccountEntity(
                "Sinh viên khóa tài khoản",
                "lock.student",
                "NOT_A_LOGIN_HASH",
                null));
        userRoles.saveAndFlush(new UserRoleEntity(
                saved,
                roles.findById(RoleCode.STUDENT).orElseThrow()));
        entityManager.clear();

        UserAccountEntity locked = accounts.findByIdForUpdate(saved.getId()).orElseThrow();

        assertEquals(LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(locked));
        assertEquals(Set.of(RoleCode.STUDENT), locked.getRoleCodes());
        assertTrue(locked.disable());
        accounts.flush();
        assertFalse(locked.isEnabled());
        assertEquals(1L, locked.getSecurityVersion());
    }
}

package vn.edu.topicregistration.shared.security;

import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.identity.persistence.DepartmentEntity;
import vn.edu.topicregistration.identity.persistence.UserAccountEntity;
import vn.edu.topicregistration.identity.persistence.UserAccountRepository;

/**
 * Central authorization rules for application services.
 *
 * <p>The authenticated username only identifies which account to reload. Roles and
 * organization scope always come from the database. Resource identifiers supplied
 * to the methods below must come from the persisted entity loaded by the calling
 * application service, never from a form field.</p>
 */
@Component
@Profile("!preview")
public class AuthorizationPolicy {
    private final UserAccountRepository accounts;

    public AuthorizationPolicy(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public ActorIdentity currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw denied();
        }

        UserAccountEntity account = accounts.findByUsername(authentication.getName())
                .filter(UserAccountEntity::isEnabled)
                .orElseThrow(AuthorizationPolicy::denied);
        Set<RoleCode> roles = account.getRoleCodes();
        if (!isValidRoleCombination(roles)) {
            throw denied();
        }

        DepartmentEntity department = account.getDepartment();
        Long departmentId = department == null ? null : department.getId();
        Long facultyId = department == null || department.getFaculty() == null
                ? null
                : department.getFaculty().getId();
        return new ActorIdentity(account.getId(), roles, departmentId, facultyId);
    }

    public ActorIdentity requireRole(RoleCode role) {
        ActorIdentity actor = currentActor();
        if (!actor.roles().contains(role)) {
            throw denied();
        }
        return actor;
    }

    public ActorIdentity requireDeanOfFaculty(long resourceFacultyId) {
        ActorIdentity actor = requireRole(RoleCode.DEAN);
        if (!Objects.equals(actor.facultyId(), resourceFacultyId)) {
            throw denied();
        }
        return actor;
    }

    public ActorIdentity requireLecturerOfDepartment(long resourceDepartmentId) {
        ActorIdentity actor = requireRole(RoleCode.LECTURER);
        if (!Objects.equals(actor.departmentId(), resourceDepartmentId)) {
            throw denied();
        }
        return actor;
    }

    public ActorIdentity requireTopicAuthoringLecturer() {
        ActorIdentity actor = requireRole(RoleCode.LECTURER);
        if (actor.roles().contains(RoleCode.DEAN)) {
            throw denied();
        }
        return actor;
    }

    public ActorIdentity requireStudentOfFaculty(long resourceFacultyId) {
        ActorIdentity actor = requireRole(RoleCode.STUDENT);
        if (!Objects.equals(actor.facultyId(), resourceFacultyId)) {
            throw denied();
        }
        return actor;
    }

    public ActorIdentity requireTopicAuthor(long persistedAuthorId, long resourceDepartmentId) {
        ActorIdentity actor = requireTopicAuthoringLecturer();
        if (!Objects.equals(actor.departmentId(), resourceDepartmentId)
                || !Objects.equals(actor.accountId(), persistedAuthorId)) {
            throw denied();
        }
        return actor;
    }

    public ActorIdentity requireGroupLeader(long persistedLeaderId) {
        ActorIdentity actor = requireRole(RoleCode.STUDENT);
        if (!Objects.equals(actor.accountId(), persistedLeaderId)) {
            throw denied();
        }
        return actor;
    }

    public ActorIdentity requireEmergencyAdmin() {
        return requireRole(RoleCode.ADMIN);
    }

    private static boolean isValidRoleCombination(Set<RoleCode> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        if (roles.contains(RoleCode.STUDENT) && roles.size() > 1) {
            return false;
        }
        return !roles.contains(RoleCode.ADMIN) || roles.size() == 1;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }

    public record ActorIdentity(
            Long accountId,
            Set<RoleCode> roles,
            Long departmentId,
            Long facultyId) {
        public ActorIdentity {
            roles = Set.copyOf(roles);
        }
    }
}

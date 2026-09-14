package vn.edu.topicregistration.security;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.DepartmentEntity;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Profile("!preview")
public class AccountDetailsService implements UserDetailsService {
    private final UserAccountRepository accounts;

    public AccountDetailsService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccountEntity account = accounts.findByUsername(username)
                .orElseThrow(AccountDetailsService::invalidCredentials);
        Set<RoleCode> roles = account.getRoleCodes();
        validateRoleCombination(roles);

        DepartmentEntity department = account.getDepartment();
        Long departmentId = department == null ? null : department.getId();
        Long facultyId = department == null ? null : department.getFaculty().getId();

        return new AccountPrincipal(
                account.getId(),
                account.getUsername(),
                account.getPasswordHash(),
                account.getFullName(),
                account.isEnabled(),
                roles,
                departmentId,
                facultyId);
    }

    private static void validateRoleCombination(Set<RoleCode> roles) {
        boolean studentMixedWithAnotherRole = roles.contains(RoleCode.STUDENT) && roles.size() > 1;
        boolean adminMixedWithAcademicRole = roles.contains(RoleCode.ADMIN) && roles.size() > 1;
        if (roles.isEmpty() || studentMixedWithAnotherRole || adminMixedWithAcademicRole) {
            throw invalidCredentials();
        }
    }

    private static UsernameNotFoundException invalidCredentials() {
        return new UsernameNotFoundException("Invalid account or role assignment");
    }
}

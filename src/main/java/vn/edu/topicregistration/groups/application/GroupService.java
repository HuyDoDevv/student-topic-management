package vn.edu.topicregistration.groups.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.groups.domain.GroupRules;
import vn.edu.topicregistration.groups.domain.GroupRules.GroupRuleException;
import vn.edu.topicregistration.groups.domain.GroupRules.MutationContext;
import vn.edu.topicregistration.groups.persistence.GroupMembershipEntity;
import vn.edu.topicregistration.groups.persistence.GroupMembershipRepository;
import vn.edu.topicregistration.groups.persistence.StudentGroupEntity;
import vn.edu.topicregistration.groups.persistence.StudentGroupRepository;
import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.identity.persistence.UserAccountEntity;
import vn.edu.topicregistration.identity.persistence.UserAccountRepository;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodEntity;
import vn.edu.topicregistration.periods.persistence.RegistrationPeriodRepository;
import vn.edu.topicregistration.shared.error.ApplicationErrorCode;
import vn.edu.topicregistration.shared.error.ApplicationException;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy.ActorIdentity;

@Service
@Profile("!preview & !test")
public class GroupService {
    private final RegistrationPeriodRepository periods;
    private final StudentGroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final UserAccountRepository accounts;
    private final ActiveRegistrationQuery activeRegistrations;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public GroupService(
            RegistrationPeriodRepository periods,
            StudentGroupRepository groups,
            GroupMembershipRepository memberships,
            UserAccountRepository accounts,
            ActiveRegistrationQuery activeRegistrations,
            AuthorizationPolicy authorization,
            Clock clock) {
        this.periods = periods;
        this.groups = groups;
        this.memberships = memberships;
        this.accounts = accounts;
        this.activeRegistrations = activeRegistrations;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView create(long periodId) {
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        ActorIdentity student = currentStudentFor(period);
        boolean alreadyMember = groups
                .findByPeriodIdAndMemberStudentId(periodId, student.accountId())
                .isPresent();
        applyRules(() -> {
            GroupRules.validateCreate(
                    clock.instant(),
                    period.getStudentStart(),
                    period.getStudentEnd(),
                    alreadyMember);
            return null;
        });

        UserAccountEntity leader = requiredAccount(student.accountId());
        StudentGroupEntity saved = groups.saveAndFlush(new StudentGroupEntity(period, leader));
        return toView(saved, student.accountId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView addMember(
            long groupId,
            long expectedVersion,
            long studentId) {
        LockedGroup locked = lockedGroup(groupId, expectedVersion);
        UserAccountEntity candidate = requiredAccount(studentId);
        boolean eligibleStudent = isEligibleStudent(
                candidate, locked.period().getFaculty().getId());
        boolean alreadyMember = groups.findByPeriodIdAndMemberStudentId(
                locked.period().getId(), studentId).isPresent();
        int slot = applyRules(() -> GroupRules.validateAdd(
                mutationContext(locked), studentId, eligibleStudent, alreadyMember));

        memberships.saveAndFlush(new GroupMembershipEntity(locked.group(), candidate, slot));
        incrementVersion(locked.group(), expectedVersion);
        return reloadVisibleGroup(groupId, locked.actor().accountId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView removeMember(
            long groupId,
            long expectedVersion,
            long studentId) {
        LockedGroup locked = lockedGroup(groupId, expectedVersion);
        applyRules(() -> {
            GroupRules.validateRemove(mutationContext(locked), studentId);
            return null;
        });
        GroupMembershipEntity membership = memberships
                .findByGroupIdAndStudentId(groupId, studentId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.INVALID_GROUP_STATE));
        memberships.delete(membership);
        memberships.flush();
        incrementVersion(locked.group(), expectedVersion);
        return reloadVisibleGroup(groupId, locked.actor().accountId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView transferLeadership(
            long groupId,
            long expectedVersion,
            long newLeaderId) {
        LockedGroup locked = lockedGroup(groupId, expectedVersion);
        applyRules(() -> {
            GroupRules.validateTransfer(mutationContext(locked), newLeaderId);
            return null;
        });
        UserAccountEntity newLeader = requiredAccount(newLeaderId);
        locked.group().transferLeadership(newLeader);
        groups.flush();
        return toView(locked.group(), locked.actor().accountId());
    }

    @Transactional(readOnly = true)
    public List<GroupPeriodView> listPeriodsForCurrentStudent() {
        ActorIdentity student = currentStudent();
        Instant now = clock.instant();
        return periods.findAllByFacultyIdOrderByLecturerStartDesc(student.facultyId()).stream()
                .map(period -> new GroupPeriodView(
                        period.getId(),
                        period.getName(),
                        period.getStudentStart(),
                        period.getStudentEnd(),
                        !now.isBefore(period.getStudentStart())
                                && !now.isAfter(period.getStudentEnd()),
                        groups.findByPeriodIdAndMemberStudentId(
                                period.getId(), student.accountId()).isPresent()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupWorkspace workspace(long periodId) {
        ActorIdentity student = currentStudent();
        RegistrationPeriodEntity period = periods
                .findByIdAndFacultyId(periodId, student.facultyId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        Optional<StudentGroupEntity> current = groups
                .findByPeriodIdAndMemberStudentId(periodId, student.accountId());
        Optional<GroupView> currentView = current.map(group -> toView(group, student.accountId()));
        List<StudentOption> candidates = currentView
                .filter(GroupView::canEdit)
                .filter(group -> group.members().size() < 3)
                .map(group -> accounts.findEligibleGroupMembers(
                                student.facultyId(), periodId, RoleCode.STUDENT)
                        .stream()
                        .map(account -> new StudentOption(
                                account.getId(), displayName(account), account.getUsername()))
                        .toList())
                .orElseGet(List::of);
        Instant now = clock.instant();
        GroupPeriodView periodView = new GroupPeriodView(
                period.getId(),
                period.getName(),
                period.getStudentStart(),
                period.getStudentEnd(),
                !now.isBefore(period.getStudentStart())
                        && !now.isAfter(period.getStudentEnd()),
                current.isPresent());
        return new GroupWorkspace(
                periodView,
                currentView,
                candidates);
    }

    @Transactional(readOnly = true)
    public GroupView getVisible(long groupId) {
        ActorIdentity student = currentStudent();
        return reloadVisibleGroup(groupId, student.accountId());
    }

    private LockedGroup lockedGroup(long groupId, long expectedVersion) {
        long periodId = groups.findPeriodIdByGroupId(groupId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        ActorIdentity actor = currentStudentFor(period);
        StudentGroupEntity group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(group.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        authorization.requireGroupLeader(group.getLeader().getId());
        if (group.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Group version is stale");
        }
        boolean activeRegistration = activeRegistrations.existsForGroup(periodId, groupId);
        return new LockedGroup(period, group, actor, activeRegistration, clock.instant());
    }

    private MutationContext mutationContext(LockedGroup locked) {
        return new MutationContext(
                locked.actor().accountId(),
                locked.group().getLeader().getId(),
                locked.group().getMembers().stream()
                        .map(GroupMembershipEntity::getStudent)
                        .map(UserAccountEntity::getId)
                        .toList(),
                locked.group().getMembers().stream()
                        .map(GroupMembershipEntity::getSlot)
                        .toList(),
                locked.activeRegistration(),
                locked.now(),
                locked.period().getStudentStart(),
                locked.period().getStudentEnd());
    }

    private void incrementVersion(StudentGroupEntity group, long expectedVersion) {
        if (groups.incrementVersion(group.getId(), expectedVersion) != 1) {
            throw new OptimisticLockingFailureException("Group version is stale");
        }
    }

    private GroupView reloadVisibleGroup(long groupId, long studentId) {
        StudentGroupEntity group = groups.findByIdVisibleToStudent(groupId, studentId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        return toView(group, studentId);
    }

    private GroupView toView(StudentGroupEntity group, long viewerId) {
        List<MemberView> memberViews = memberships.findAllByGroupIdOrderBySlot(group.getId())
                .stream()
                .map(membership -> new MemberView(
                        membership.getStudent().getId(),
                        displayName(membership.getStudent()),
                        membership.getStudent().getUsername(),
                        membership.getSlot(),
                        Objects.equals(
                                membership.getStudent().getId(),
                                group.getLeader().getId())))
                .toList();
        boolean canManage = Objects.equals(group.getLeader().getId(), viewerId);
        Instant now = clock.instant();
        boolean studentOpen = !now.isBefore(group.getPeriod().getStudentStart())
                && !now.isAfter(group.getPeriod().getStudentEnd());
        boolean activeRegistration = activeRegistrations.existsForGroup(
                group.getPeriod().getId(), group.getId());
        return new GroupView(
                group.getId(),
                group.getPeriod().getId(),
                group.getLeader().getId(),
                group.getVersion(),
                memberViews,
                canManage,
                canManage && studentOpen && !activeRegistration,
                studentOpen,
                activeRegistration);
    }

    private RegistrationPeriodEntity lockedPeriod(long periodId) {
        return periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private ActorIdentity currentStudentFor(RegistrationPeriodEntity period) {
        ActorIdentity student = currentStudent();
        if (!Objects.equals(student.facultyId(), period.getFaculty().getId())) {
            throw new AccessDeniedException("Student scope does not match the period");
        }
        return student;
    }

    private ActorIdentity currentStudent() {
        ActorIdentity student = authorization.requireRole(RoleCode.STUDENT);
        if (student.accountId() == null || student.facultyId() == null) {
            throw new AccessDeniedException("Student organization scope is missing");
        }
        return student;
    }

    private UserAccountEntity requiredAccount(long accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
    }

    private boolean isEligibleStudent(UserAccountEntity account, long facultyId) {
        return account.isEnabled()
                && account.getRoleCodes().contains(RoleCode.STUDENT)
                && account.getDepartment() != null
                && account.getDepartment().getFaculty() != null
                && Objects.equals(account.getDepartment().getFaculty().getId(), facultyId);
    }

    private <T> T applyRules(RuleOperation<T> operation) {
        try {
            return operation.run();
        } catch (GroupRuleException exception) {
            switch (exception.failure()) {
                case ACCESS_DENIED -> throw new AccessDeniedException("Access denied");
                case WINDOW_CLOSED -> throw new ApplicationException(
                        ApplicationErrorCode.WINDOW_CLOSED);
                case ACTIVE_REGISTRATION -> throw new ApplicationException(
                        ApplicationErrorCode.ACTIVE_REGISTRATION_EXISTS);
                case GROUP_FULL -> throw new ApplicationException(
                        ApplicationErrorCode.GROUP_FULL);
                case ALREADY_MEMBER -> throw new ApplicationException(
                        ApplicationErrorCode.GROUP_MEMBERSHIP_CONFLICT);
                case INVALID_MEMBER, INVALID_STATE -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_GROUP_STATE);
            }
            throw new IllegalStateException("Unknown group rule failure", exception);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    private static String displayName(UserAccountEntity account) {
        return account.getFullName() == null || account.getFullName().isBlank()
                ? account.getUsername()
                : account.getFullName();
    }

    @FunctionalInterface
    private interface RuleOperation<T> {
        T run();
    }

    private record LockedGroup(
            RegistrationPeriodEntity period,
            StudentGroupEntity group,
            ActorIdentity actor,
            boolean activeRegistration,
            Instant now) {
    }

    public record GroupPeriodView(
            long id,
            String name,
            Instant studentStart,
            Instant studentEnd,
            boolean studentOpen,
            boolean hasGroup) {
    }

    public record GroupWorkspace(
            GroupPeriodView period,
            Optional<GroupView> group,
            List<StudentOption> candidates) {
        public GroupWorkspace {
            group = group == null ? Optional.empty() : group;
            candidates = List.copyOf(candidates);
        }
    }

    public record GroupView(
            long id,
            long periodId,
            long leaderId,
            long version,
            List<MemberView> members,
            boolean canManage,
            boolean canEdit,
            boolean studentOpen,
            boolean activeRegistration) {
        public GroupView {
            members = List.copyOf(members);
        }
    }

    public record MemberView(
            long id,
            String fullName,
            String username,
            int slot,
            boolean leader) {
    }

    public record StudentOption(
            long id,
            String fullName,
            String username) {
    }
}

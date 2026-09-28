package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.GroupInvitationEntity;
import vn.edu.topicregistration.repository.GroupInvitationRepository;
import vn.edu.topicregistration.entity.GroupInvitationStatus;
import vn.edu.topicregistration.entity.GroupMembershipEntity;
import vn.edu.topicregistration.repository.GroupMembershipRepository;
import vn.edu.topicregistration.entity.StudentGroupEntity;
import vn.edu.topicregistration.repository.StudentGroupRepository;
import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;

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

import vn.edu.topicregistration.service.ActiveRegistrationQuery.ActiveRegistration;
import vn.edu.topicregistration.service.ActiveRegistrationQuery.ActiveRegistrationStatus;
import vn.edu.topicregistration.service.GroupRules.GroupRuleException;
import vn.edu.topicregistration.service.GroupRules.MutationContext;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.entity.NotificationEventType;
import vn.edu.topicregistration.entity.NotificationTargetType;
import vn.edu.topicregistration.service.NotificationService.EmissionCommand;

@Service
@Profile("!test")
public class GroupService {
    private final RegistrationPeriodRepository periods;
    private final StudentGroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final GroupInvitationRepository invitations;
    private final UserAccountRepository accounts;
    private final ActiveRegistrationQuery activeRegistrations;
    private final AuthorizationPolicy authorization;
    private final NotificationService notifications;
    private final Clock clock;

    public GroupService(
            RegistrationPeriodRepository periods,
            StudentGroupRepository groups,
            GroupMembershipRepository memberships,
            GroupInvitationRepository invitations,
            UserAccountRepository accounts,
            ActiveRegistrationQuery activeRegistrations,
            AuthorizationPolicy authorization,
            NotificationService notifications,
            Clock clock) {
        this.periods = periods;
        this.groups = groups;
        this.memberships = memberships;
        this.invitations = invitations;
        this.accounts = accounts;
        this.activeRegistrations = activeRegistrations;
        this.authorization = authorization;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView create(long periodId) {
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        ActorIdentity student = currentStudentFor(period);
        boolean alreadyMember = groups
                .findByPeriodIdAndMemberStudentId(periodId, student.accountId())
                .isPresent();
        boolean hasPendingInvitation = invitations.existsByPeriodIdAndStudentIdAndStatus(
                periodId, student.accountId(), GroupInvitationStatus.PENDING);
        applyRules(() -> {
            GroupRules.validateCreate(
                    clock.instant(),
                    period.getStudentStart(),
                    period.getStudentEnd(),
                    alreadyMember,
                    hasPendingInvitation);
            return null;
        });

        UserAccountEntity leader = requiredAccount(student.accountId());
        StudentGroupEntity saved = groups.saveAndFlush(new StudentGroupEntity(period, leader));
        return toView(saved, student.accountId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView inviteMember(
            long groupId,
            long expectedGroupVersion,
            long studentId) {
        LockedGroup locked = lockedGroup(groupId, expectedGroupVersion, true);
        UserAccountEntity candidate = requiredAccount(studentId);
        boolean eligibleStudent = isEligibleStudent(
                candidate, locked.period().getFaculty().getId());
        boolean alreadyMember = groups.findByPeriodIdAndMemberStudentId(
                locked.period().getId(), studentId).isPresent();
        boolean alreadyPending = invitations.existsByPeriodIdAndStudentIdAndStatus(
                locked.period().getId(), studentId, GroupInvitationStatus.PENDING);
        int slot = applyRules(() -> GroupRules.validateInvite(
                mutationContext(locked),
                studentId,
                eligibleStudent,
                alreadyMember || alreadyPending));

        GroupInvitationEntity invitation = invitations.saveAndFlush(new GroupInvitationEntity(
                locked.group(),
                candidate,
                requiredAccount(locked.actor().accountId()),
                slot,
                locked.now()));
        notifications.emit(new EmissionCommand(
                NotificationEventType.GROUP_INVITATION_CREATED,
                invitation.getId(),
                locked.actor().accountId(),
                List.of(candidate.getId()),
                "Bạn nhận được lời mời vào nhóm",
                "Một trưởng nhóm đã mời bạn tham gia nhóm sinh viên.",
                null,
                NotificationTargetType.GROUP_INVITATION,
                invitation.getId()));
        incrementVersion(locked.group(), expectedGroupVersion);
        return reloadVisibleGroup(groupId, locked.actor().accountId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long acceptInvitation(long invitationId, long expectedVersion) {
        InvitationRoute route = invitationRoute(invitationId);
        RegistrationPeriodEntity period = lockedPeriod(route.periodId());
        ActorIdentity student = currentStudentFor(period);
        StudentGroupEntity group = lockedActiveGroup(route.groupId(), period);
        GroupInvitationEntity invitation = lockedInvitation(invitationId, route, expectedVersion);
        boolean alreadyMember = groups.findByPeriodIdAndMemberStudentId(
                period.getId(), student.accountId()).isPresent();
        boolean activeRegistration = activeRegistrations.existsForGroup(
                period.getId(), group.getId());
        Instant now = clock.instant();
        MutationContext context = mutationContext(
                period, group, student, activeRegistration, now);
        applyRules(() -> {
            GroupRules.validateAccept(
                    context,
                    invitation.getStudent().getId(),
                    invitation.getSlot(),
                    invitation.getStatus() == GroupInvitationStatus.PENDING,
                    alreadyMember);
            return null;
        });

        invitation.accept(now);
        invitations.saveAndFlush(invitation);
        memberships.saveAndFlush(new GroupMembershipEntity(
                group, invitation.getStudent(), invitation.getSlot()));
        incrementVersion(group, group.getVersion());
        notifications.emit(new EmissionCommand(
                NotificationEventType.GROUP_INVITATION_ACCEPTED,
                invitation.getId(),
                student.accountId(),
                List.of(invitation.getInvitedBy().getId()),
                "Lời mời nhóm đã được chấp nhận",
                "Sinh viên được mời đã tham gia nhóm của bạn.",
                null,
                NotificationTargetType.GROUP,
                group.getId()));
        return group.getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long rejectInvitation(long invitationId, long expectedVersion) {
        InvitationRoute route = invitationRoute(invitationId);
        RegistrationPeriodEntity period = lockedPeriod(route.periodId());
        ActorIdentity student = currentStudentFor(period);
        Optional<StudentGroupEntity> activeGroup = groups.findByIdForUpdate(route.groupId());
        GroupInvitationEntity invitation = lockedInvitation(invitationId, route, expectedVersion);
        applyRules(() -> {
            GroupRules.validateReject(
                    student.accountId(),
                    invitation.getStudent().getId(),
                    invitation.getStatus() == GroupInvitationStatus.PENDING);
            return null;
        });

        invitation.reject(clock.instant());
        invitations.saveAndFlush(invitation);
        activeGroup.ifPresent(group -> incrementVersion(group, group.getVersion()));
        notifications.emit(new EmissionCommand(
                NotificationEventType.GROUP_INVITATION_REJECTED,
                invitation.getId(),
                student.accountId(),
                List.of(invitation.getInvitedBy().getId()),
                "Lời mời nhóm đã bị từ chối",
                "Sinh viên được mời đã từ chối tham gia nhóm của bạn.",
                null,
                NotificationTargetType.GROUP,
                route.groupId()));
        return route.periodId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long cancelInvitation(long invitationId, long expectedVersion) {
        InvitationRoute route = invitationRoute(invitationId);
        RegistrationPeriodEntity period = lockedPeriod(route.periodId());
        ActorIdentity student = currentStudentFor(period);
        StudentGroupEntity group = lockedActiveGroup(route.groupId(), period);
        authorization.requireGroupLeader(group.getLeader().getId());
        GroupInvitationEntity invitation = lockedInvitation(invitationId, route, expectedVersion);
        boolean activeRegistration = activeRegistrations.existsForGroup(
                period.getId(), group.getId());
        Instant now = clock.instant();
        MutationContext context = mutationContext(
                period, group, student, activeRegistration, now);
        applyRules(() -> {
            GroupRules.validateCancel(
                    context,
                    invitation.getStatus() == GroupInvitationStatus.PENDING);
            return null;
        });

        invitation.cancel(now);
        invitations.saveAndFlush(invitation);
        incrementVersion(group, group.getVersion());
        return group.getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView removeMember(
            long groupId,
            long expectedVersion,
            long studentId) {
        LockedGroup locked = lockedGroup(groupId, expectedVersion, true);
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
    public long leave(long groupId, long expectedVersion) {
        LockedGroup locked = lockedGroup(groupId, expectedVersion, false);
        applyRules(() -> {
            GroupRules.validateLeave(mutationContext(locked));
            return null;
        });
        GroupMembershipEntity membership = memberships
                .findByGroupIdAndStudentId(groupId, locked.actor().accountId())
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        memberships.delete(membership);
        memberships.flush();
        incrementVersion(locked.group(), expectedVersion);
        return locked.period().getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long dissolve(long groupId, long expectedVersion) {
        LockedGroup locked = lockedGroup(groupId, expectedVersion, true);
        MutationContext context = mutationContext(locked);
        applyRules(() -> {
            GroupRules.validateDissolve(context);
            return null;
        });

        List<GroupInvitationEntity> pending = pendingInvitations(groupId);
        pending.forEach(invitation -> invitation.cancel(locked.now()));
        invitations.saveAllAndFlush(pending);
        locked.group().dissolve(locked.now());
        groups.saveAndFlush(locked.group());
        GroupMembershipEntity leaderMembership = memberships
                .findByGroupIdAndStudentId(groupId, locked.actor().accountId())
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.INVALID_GROUP_STATE));
        memberships.delete(leaderMembership);
        memberships.flush();
        return locked.period().getId();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public GroupView transferLeadership(
            long groupId,
            long expectedVersion,
            long newLeaderId) {
        LockedGroup locked = lockedGroup(groupId, expectedVersion, true);
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
                        StudentPeriodPhase.at(
                                now, period.getStudentStart(), period.getStudentEnd()),
                        groups.findByPeriodIdAndMemberStudentId(
                                period.getId(), student.accountId()).isPresent(),
                        invitations.existsByPeriodIdAndStudentIdAndStatus(
                                period.getId(),
                                student.accountId(),
                                GroupInvitationStatus.PENDING)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationView> listInvitationsForCurrentStudent() {
        ActorIdentity student = currentStudent();
        return invitations.findAllByStudentId(student.accountId()).stream()
                .filter(invitation -> Objects.equals(
                        invitation.getPeriod().getFaculty().getId(), student.facultyId()))
                .map(invitation -> toInvitationView(invitation, student.accountId()))
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
        List<InvitationView> outgoing = current
                .filter(group -> Objects.equals(group.getLeader().getId(), student.accountId()))
                .map(group -> invitations.findAllByGroupId(group.getId()).stream()
                        .map(invitation -> toInvitationView(invitation, student.accountId()))
                        .toList())
                .orElseGet(List::of);
        int reservedSlots = currentView.map(GroupView::reservedSlots).orElse(0);
        List<StudentOption> candidates = currentView
                .filter(GroupView::canEdit)
                .filter(group -> reservedSlots < 3)
                .map(group -> accounts.findEligibleGroupMembers(
                                student.facultyId(),
                                periodId,
                                RoleCode.STUDENT,
                                GroupInvitationStatus.PENDING)
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
                StudentPeriodPhase.at(
                        now, period.getStudentStart(), period.getStudentEnd()),
                current.isPresent(),
                invitations.existsByPeriodIdAndStudentIdAndStatus(
                        periodId, student.accountId(), GroupInvitationStatus.PENDING));
        return new GroupWorkspace(periodView, currentView, candidates, outgoing);
    }

    @Transactional(readOnly = true)
    public GroupView getVisible(long groupId) {
        ActorIdentity student = currentStudent();
        return reloadVisibleGroup(groupId, student.accountId());
    }

    @Transactional(readOnly = true)
    public boolean hasPendingInvitation(long groupId) {
        return invitations.existsByGroupIdAndStatus(groupId, GroupInvitationStatus.PENDING);
    }

    private LockedGroup lockedGroup(
            long groupId,
            long expectedVersion,
            boolean requireLeader) {
        long periodId = groups.findPeriodIdByGroupId(groupId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = lockedPeriod(periodId);
        ActorIdentity actor = currentStudentFor(period);
        StudentGroupEntity group = lockedActiveGroup(groupId, period);
        if (requireLeader) {
            authorization.requireGroupLeader(group.getLeader().getId());
        }
        if (group.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Group version is stale");
        }
        boolean activeRegistration = activeRegistrations.existsForGroup(periodId, groupId);
        return new LockedGroup(
                period, group, actor, activeRegistration, clock.instant());
    }

    private StudentGroupEntity lockedActiveGroup(
            long groupId,
            RegistrationPeriodEntity period) {
        StudentGroupEntity group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(group.getPeriod().getId(), period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        return group;
    }

    private InvitationRoute invitationRoute(long invitationId) {
        long periodId = invitations.findPeriodIdByInvitationId(invitationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        long groupId = invitations.findGroupIdByInvitationId(invitationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        return new InvitationRoute(periodId, groupId);
    }

    private GroupInvitationEntity lockedInvitation(
            long invitationId,
            InvitationRoute route,
            long expectedVersion) {
        GroupInvitationEntity invitation = invitations.findByIdForUpdate(invitationId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!Objects.equals(invitation.getPeriod().getId(), route.periodId())
                || !Objects.equals(invitation.getGroup().getId(), route.groupId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (invitation.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Invitation version is stale");
        }
        return invitation;
    }

    private MutationContext mutationContext(LockedGroup locked) {
        return mutationContext(
                locked.period(),
                locked.group(),
                locked.actor(),
                locked.activeRegistration(),
                locked.now());
    }

    private MutationContext mutationContext(
            RegistrationPeriodEntity period,
            StudentGroupEntity group,
            ActorIdentity actor,
            boolean activeRegistration,
            Instant now) {
        return new MutationContext(
                actor.accountId(),
                group.getLeader().getId(),
                group.getMembers().stream()
                        .map(GroupMembershipEntity::getStudent)
                        .map(UserAccountEntity::getId)
                        .toList(),
                group.getMembers().stream()
                        .map(GroupMembershipEntity::getSlot)
                        .toList(),
                pendingInvitations(group.getId()).stream()
                        .map(GroupInvitationEntity::getSlot)
                        .toList(),
                activeRegistration,
                now,
                period.getStudentStart(),
                period.getStudentEnd());
    }

    private List<GroupInvitationEntity> pendingInvitations(long groupId) {
        return invitations.findAllByGroupIdAndStatusOrderBySlotAsc(
                groupId, GroupInvitationStatus.PENDING);
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
        boolean studentOpen = isStudentWindowOpen(group.getPeriod(), now);
        ActiveRegistration activeRegistration = activeRegistrations.findForGroup(
                group.getPeriod().getId(), group.getId()).orElse(null);
        boolean hasActiveRegistration = activeRegistration != null;
        int pendingCount = Math.toIntExact(invitations.countByGroupIdAndStatus(
                group.getId(), GroupInvitationStatus.PENDING));
        return new GroupView(
                group.getId(),
                group.getPeriod().getId(),
                group.getLeader().getId(),
                group.getVersion(),
                memberViews,
                canManage,
                canManage && studentOpen && !hasActiveRegistration,
                !canManage && studentOpen && !hasActiveRegistration,
                canManage && memberViews.size() == 1 && studentOpen && !hasActiveRegistration,
                studentOpen,
                display(activeRegistration),
                pendingCount);
    }

    private static GroupRegistrationView display(ActiveRegistration registration) {
        if (registration == null) {
            return null;
        }
        boolean accepted = registration.status() == ActiveRegistrationStatus.ACCEPTED;
        return new GroupRegistrationView(
                registration.id(),
                registration.topicTitle(),
                registration.status().name(),
                accepted ? "Đã chấp nhận" : "Chờ xét",
                accepted ? "bg-green-lt" : "bg-yellow-lt",
                accepted);
    }

    private InvitationView toInvitationView(
            GroupInvitationEntity invitation,
            long viewerId) {
        Instant now = clock.instant();
        RegistrationPeriodEntity period = invitation.getPeriod();
        StudentGroupEntity group = invitation.getGroup();
        boolean pending = invitation.getStatus() == GroupInvitationStatus.PENDING;
        boolean expired = pending && now.isAfter(period.getStudentEnd());
        boolean groupActive = group.isActive();
        boolean studentOpen = isStudentWindowOpen(period, now);
        boolean activeRegistration = groupActive && activeRegistrations.existsForGroup(
                period.getId(), group.getId());
        Optional<StudentGroupEntity> currentGroup = groups.findByPeriodIdAndMemberStudentId(
                period.getId(), invitation.getStudent().getId());
        boolean alreadyMember = currentGroup.isPresent();
        boolean canViewGroup = groupActive && currentGroup
                .map(item -> Objects.equals(item.getId(), group.getId()))
                .orElse(false);
        String effectiveStatus = expired ? "EXPIRED" : invitation.getStatus().name();
        return new InvitationView(
                invitation.getId(),
                group.getId(),
                period.getId(),
                period.getName(),
                invitation.getStudent().getId(),
                displayName(invitation.getStudent()),
                invitation.getStudent().getUsername(),
                displayName(invitation.getInvitedBy()),
                invitation.getSlot(),
                effectiveStatus,
                invitation.getCreatedAt(),
                invitation.getRespondedAt(),
                invitation.getVersion(),
                groupActive,
                canViewGroup,
                pending
                        && !expired
                        && groupActive
                        && studentOpen
                        && !activeRegistration
                        && !alreadyMember
                        && Objects.equals(invitation.getStudent().getId(), viewerId),
                pending && Objects.equals(invitation.getStudent().getId(), viewerId),
                pending
                        && groupActive
                        && studentOpen
                        && !activeRegistration
                        && Objects.equals(group.getLeader().getId(), viewerId));
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

    private static boolean isStudentWindowOpen(
            RegistrationPeriodEntity period,
            Instant now) {
        return !now.isBefore(period.getStudentStart()) && !now.isAfter(period.getStudentEnd());
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
                case INVITATION_CONFLICT -> throw new ApplicationException(
                        ApplicationErrorCode.GROUP_INVITATION_CONFLICT);
                case INVALID_INVITATION -> throw new ApplicationException(
                        ApplicationErrorCode.INVALID_INVITATION_STATE);
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

    private record InvitationRoute(long periodId, long groupId) {
    }

    public record GroupPeriodView(
            long id,
            String name,
            Instant studentStart,
            Instant studentEnd,
            StudentPeriodPhase phase,
            boolean hasGroup,
            boolean hasPendingInvitation) {
        public boolean studentOpen() {
            return phase == StudentPeriodPhase.OPEN;
        }
    }

    public enum StudentPeriodPhase {
        UPCOMING,
        OPEN,
        ENDED;

        public static StudentPeriodPhase at(Instant now, Instant start, Instant end) {
            Objects.requireNonNull(now, "now");
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (now.isBefore(start)) {
                return UPCOMING;
            }
            return now.isAfter(end) ? ENDED : OPEN;
        }
    }

    public record GroupWorkspace(
            GroupPeriodView period,
            Optional<GroupView> group,
            List<StudentOption> candidates,
            List<InvitationView> invitations) {
        public GroupWorkspace {
            group = group == null ? Optional.empty() : group;
            candidates = List.copyOf(candidates);
            invitations = List.copyOf(invitations);
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
            boolean canLeave,
            boolean canDissolve,
            boolean studentOpen,
            GroupRegistrationView registration,
            int pendingInvitationCount) {
        public GroupView {
            members = List.copyOf(members);
        }

        public int reservedSlots() {
            return members.size() + pendingInvitationCount;
        }

        public boolean activeRegistration() {
            return registration != null;
        }
    }

    public record GroupRegistrationView(
            long id,
            String topicTitle,
            String status,
            String statusLabel,
            String statusClass,
            boolean accepted) {
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

    public record InvitationView(
            long id,
            long groupId,
            long periodId,
            String periodName,
            long studentId,
            String studentName,
            String studentUsername,
            String invitedByName,
            int slot,
            String status,
            Instant createdAt,
            Instant respondedAt,
            long version,
            boolean groupActive,
            boolean canViewGroup,
            boolean canAccept,
            boolean canReject,
            boolean canCancel) {
    }
}

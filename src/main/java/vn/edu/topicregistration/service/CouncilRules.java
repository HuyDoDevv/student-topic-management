package vn.edu.topicregistration.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import vn.edu.topicregistration.entity.CouncilStatus;
import vn.edu.topicregistration.entity.RegistrationStatus;

public final class CouncilRules {
    private static final Set<String> SUPPORTED_TYPES = Set.of("KLTN");

    private CouncilRules() {
    }

    public static void validateCreation(
            String topicType,
            long facultyId,
            long chairId,
            long secretaryId,
            List<MemberCandidate> members) {
        if (!SUPPORTED_TYPES.contains(topicType)) {
            throw failure(Failure.INVALID_STATE);
        }
        if (members == null || members.size() < 3 || members.size() > 5
                || chairId == secretaryId) {
            throw failure(Failure.INVALID_MEMBERS);
        }

        Set<Long> uniqueIds = new HashSet<>();
        boolean chairPresent = false;
        boolean secretaryPresent = false;
        for (MemberCandidate member : members) {
            if (member == null || member.accountId() == null
                    || !uniqueIds.add(member.accountId())
                    || !member.enabled()
                    || !member.lecturer()
                    || !Objects.equals(member.facultyId(), facultyId)) {
                throw failure(Failure.INVALID_MEMBERS);
            }
            chairPresent |= member.accountId() == chairId;
            secretaryPresent |= member.accountId() == secretaryId;
        }
        if (!chairPresent || !secretaryPresent) {
            throw failure(Failure.INVALID_MEMBERS);
        }
    }

    public static void validateAssignment(AssignmentContext context) {
        Objects.requireNonNull(context, "context");
        if (context.councilStatus() != CouncilStatus.DRAFT
                && context.councilStatus() != CouncilStatus.ASSIGNED) {
            throw failure(Failure.INVALID_STATE);
        }
        if (!SUPPORTED_TYPES.contains(context.topicType())
                || context.registrationStatus() != RegistrationStatus.ACCEPTED
                || !context.samePeriod()
                || !context.sameFaculty()
                || context.assignedElsewhere()
                || context.supervisorConflict()) {
            throw failure(Failure.INVALID_ASSIGNMENT);
        }
    }

    public static void validateTransition(TransitionContext context) {
        Objects.requireNonNull(context, "context");
        boolean valid = switch (context.current()) {
            case DRAFT -> context.target() == CouncilStatus.ASSIGNED
                    && validCardinality(context.memberCount())
                    && context.assignmentCount() >= 1;
            case ASSIGNED -> context.target() == CouncilStatus.SCHEDULED
                    && validCardinality(context.memberCount())
                    && context.assignmentCount() >= 1
                    && context.allAssignmentsScheduled();
            case SCHEDULED -> context.target() == CouncilStatus.ACTIVE
                    && validCardinality(context.memberCount())
                    && context.assignmentCount() >= 1
                    && context.allAssignmentsScheduled()
                    && context.membersStillValid()
                    && !context.supervisorConflict()
                    && context.allReportsPresent();
            default -> false;
        };
        if (!valid) {
            throw failure(Failure.INVALID_STATE);
        }
    }

    public static boolean isSupportedTopicType(String topicType) {
        return SUPPORTED_TYPES.contains(topicType);
    }

    private static boolean validCardinality(int memberCount) {
        return memberCount >= 3 && memberCount <= 5;
    }

    private static CouncilRuleException failure(Failure failure) {
        return new CouncilRuleException(failure);
    }

    public record MemberCandidate(
            Long accountId,
            Long facultyId,
            boolean enabled,
            boolean lecturer) {
    }

    public record AssignmentContext(
            CouncilStatus councilStatus,
            String topicType,
            RegistrationStatus registrationStatus,
            boolean samePeriod,
            boolean sameFaculty,
            boolean assignedElsewhere,
            boolean supervisorConflict) {
        public AssignmentContext withCouncilStatus(CouncilStatus value) {
            return new AssignmentContext(
                    value, topicType, registrationStatus, samePeriod, sameFaculty,
                    assignedElsewhere, supervisorConflict);
        }

        public AssignmentContext withTopicType(String value) {
            return new AssignmentContext(
                    councilStatus, value, registrationStatus, samePeriod, sameFaculty,
                    assignedElsewhere, supervisorConflict);
        }

        public AssignmentContext withRegistrationStatus(RegistrationStatus value) {
            return new AssignmentContext(
                    councilStatus, topicType, value, samePeriod, sameFaculty,
                    assignedElsewhere, supervisorConflict);
        }

        public AssignmentContext withSamePeriod(boolean value) {
            return new AssignmentContext(
                    councilStatus, topicType, registrationStatus, value, sameFaculty,
                    assignedElsewhere, supervisorConflict);
        }

        public AssignmentContext withSameFaculty(boolean value) {
            return new AssignmentContext(
                    councilStatus, topicType, registrationStatus, samePeriod, value,
                    assignedElsewhere, supervisorConflict);
        }

        public AssignmentContext withAssignedElsewhere(boolean value) {
            return new AssignmentContext(
                    councilStatus, topicType, registrationStatus, samePeriod, sameFaculty,
                    value, supervisorConflict);
        }

        public AssignmentContext withSupervisorConflict(boolean value) {
            return new AssignmentContext(
                    councilStatus, topicType, registrationStatus, samePeriod, sameFaculty,
                    assignedElsewhere, value);
        }
    }

    public record TransitionContext(
            CouncilStatus current,
            CouncilStatus target,
            int memberCount,
            int assignmentCount,
            boolean allAssignmentsScheduled,
            boolean membersStillValid,
            boolean supervisorConflict,
            boolean allReportsPresent) {
    }

    public enum Failure {
        INVALID_MEMBERS,
        INVALID_ASSIGNMENT,
        INVALID_STATE
    }

    public static final class CouncilRuleException extends RuntimeException {
        private final Failure failure;

        public CouncilRuleException(Failure failure) {
            super(Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}

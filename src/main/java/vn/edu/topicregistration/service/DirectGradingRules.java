package vn.edu.topicregistration.service;

import java.util.Objects;
import java.util.Set;

import vn.edu.topicregistration.entity.RegistrationStatus;

public final class DirectGradingRules {
    private static final Set<String> SUPPORTED_TYPES = Set.of("COURSE", "NCKH", "TLCN");

    private DirectGradingRules() {
    }

    public static boolean isSupportedTopicType(String topicType) {
        return SUPPORTED_TYPES.contains(topicType);
    }

    public static void validate(AssignmentContext context) {
        Objects.requireNonNull(context, "context");
        if (!isSupportedTopicType(context.topicType())) {
            throw failure(Failure.UNSUPPORTED_TYPE);
        }
        if (context.registrationStatus() != RegistrationStatus.ACCEPTED) {
            throw failure(Failure.INVALID_REGISTRATION);
        }
        if (!context.reportPresent()) {
            throw failure(Failure.REPORT_REQUIRED);
        }
        if (context.alreadyAssigned()) {
            throw failure(Failure.ALREADY_ASSIGNED);
        }
        if (!context.graderEnabled()
                || !context.graderLecturer()
                || !context.sameFaculty()) {
            throw failure(Failure.INVALID_GRADER);
        }
        if (context.supervisor()) {
            throw failure(Failure.SUPERVISOR_CONFLICT);
        }
    }

    private static DirectGradingRuleException failure(Failure failure) {
        return new DirectGradingRuleException(failure);
    }

    public record AssignmentContext(
            String topicType,
            RegistrationStatus registrationStatus,
            boolean reportPresent,
            boolean alreadyAssigned,
            boolean graderEnabled,
            boolean graderLecturer,
            boolean sameFaculty,
            boolean supervisor) {
        public AssignmentContext withTopicType(String value) {
            return new AssignmentContext(
                    value, registrationStatus, reportPresent, alreadyAssigned,
                    graderEnabled, graderLecturer, sameFaculty, supervisor);
        }

        public AssignmentContext withRegistrationStatus(RegistrationStatus value) {
            return new AssignmentContext(
                    topicType, value, reportPresent, alreadyAssigned,
                    graderEnabled, graderLecturer, sameFaculty, supervisor);
        }

        public AssignmentContext withReportPresent(boolean value) {
            return new AssignmentContext(
                    topicType, registrationStatus, value, alreadyAssigned,
                    graderEnabled, graderLecturer, sameFaculty, supervisor);
        }

        public AssignmentContext withAlreadyAssigned(boolean value) {
            return new AssignmentContext(
                    topicType, registrationStatus, reportPresent, value,
                    graderEnabled, graderLecturer, sameFaculty, supervisor);
        }

        public AssignmentContext withGraderEnabled(boolean value) {
            return new AssignmentContext(
                    topicType, registrationStatus, reportPresent, alreadyAssigned,
                    value, graderLecturer, sameFaculty, supervisor);
        }

        public AssignmentContext withGraderLecturer(boolean value) {
            return new AssignmentContext(
                    topicType, registrationStatus, reportPresent, alreadyAssigned,
                    graderEnabled, value, sameFaculty, supervisor);
        }

        public AssignmentContext withSameFaculty(boolean value) {
            return new AssignmentContext(
                    topicType, registrationStatus, reportPresent, alreadyAssigned,
                    graderEnabled, graderLecturer, value, supervisor);
        }

        public AssignmentContext withSupervisor(boolean value) {
            return new AssignmentContext(
                    topicType, registrationStatus, reportPresent, alreadyAssigned,
                    graderEnabled, graderLecturer, sameFaculty, value);
        }
    }

    public enum Failure {
        UNSUPPORTED_TYPE,
        INVALID_REGISTRATION,
        REPORT_REQUIRED,
        ALREADY_ASSIGNED,
        INVALID_GRADER,
        SUPERVISOR_CONFLICT
    }

    public static final class DirectGradingRuleException extends RuntimeException {
        private final Failure failure;

        private DirectGradingRuleException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}

package vn.edu.topicregistration.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TopicRules {
    private TopicRules() {
    }

    public static TopicDraft validateForSave(TopicDraft request, TopicAccess access) {
        requireDraftAuthor(access);
        TopicDraft normalized = normalize(request);
        validateSupervisors(
                normalized.supervisors(),
                access.authorId(),
                access.topicFacultyId(),
                access.topicDepartmentId());
        return normalized;
    }

    public static TopicDraft validateForSubmit(
            TopicDraft request,
            TopicAccess access,
            Instant now,
            Instant lecturerStart,
            Instant lecturerEnd) {
        TopicDraft normalized = validateForSave(request, access);
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lecturerStart, "lecturerStart");
        Objects.requireNonNull(lecturerEnd, "lecturerEnd");

        if (now.isBefore(lecturerStart) || now.isAfter(lecturerEnd)) {
            throw failure(Failure.WINDOW_CLOSED);
        }
        if (isBlank(normalized.title())
                || isBlank(normalized.description())
                || isBlank(normalized.objectives())
                || isBlank(normalized.studentRequirements())
                || isBlank(normalized.expectedResults())) {
            throw failure(Failure.VALIDATION_FAILED);
        }
        return normalized;
    }

    private static void requireDraftAuthor(TopicAccess access) {
        Objects.requireNonNull(access, "access");
        if (access.status() != TopicStatus.DRAFT) {
            throw failure(Failure.INVALID_STATE);
        }
        if (access.actorId() != access.authorId()
                || access.actorFacultyId() != access.topicFacultyId()) {
            throw failure(Failure.ACCESS_DENIED);
        }
    }

    private static TopicDraft normalize(TopicDraft request) {
        Objects.requireNonNull(request, "request");
        return new TopicDraft(
                strip(request.title()),
                strip(request.description()),
                strip(request.objectives()),
                strip(request.studentRequirements()),
                strip(request.expectedResults()),
                request.supervisors());
    }

    private static void validateSupervisors(
            List<SupervisorCandidate> supervisors,
            long authorId,
            long topicFacultyId,
            Long topicDepartmentId) {
        if (topicDepartmentId == null
                || supervisors.isEmpty()
                || supervisors.size() > 2
                || supervisors.getFirst().accountId() != authorId) {
            throw failure(Failure.VALIDATION_FAILED);
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (SupervisorCandidate supervisor : supervisors) {
            if (supervisor == null
                    || !uniqueIds.add(supervisor.accountId())
                    || !supervisor.enabled()
                    || !supervisor.lecturer()
                    || supervisor.facultyId() != topicFacultyId
                    || !Objects.equals(supervisor.departmentId(), topicDepartmentId)) {
                throw failure(Failure.VALIDATION_FAILED);
            }
        }
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static TopicRuleException failure(Failure failure) {
        return new TopicRuleException(failure);
    }

    public enum TopicStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        PUBLISHED
    }

    public enum Failure {
        ACCESS_DENIED,
        INVALID_STATE,
        VALIDATION_FAILED,
        WINDOW_CLOSED
    }

    public record TopicAccess(
            long actorId,
            long authorId,
            long actorFacultyId,
            long topicFacultyId,
            Long topicDepartmentId,
            TopicStatus status) {
        public TopicAccess {
            Objects.requireNonNull(status, "status");
        }
    }

    public record SupervisorCandidate(
            long accountId,
            long facultyId,
            Long departmentId,
            boolean enabled,
            boolean lecturer) {
    }

    public record TopicDraft(
            String title,
            String description,
            String objectives,
            String studentRequirements,
            String expectedResults,
            List<SupervisorCandidate> supervisors) {
        public TopicDraft {
            supervisors = supervisors == null ? List.of() : List.copyOf(supervisors);
        }
    }

    public static final class TopicRuleException extends RuntimeException {
        private final Failure failure;

        public TopicRuleException(Failure failure) {
            super(Objects.requireNonNull(failure, "failure").name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}

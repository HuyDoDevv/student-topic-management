package vn.edu.topicregistration.periods.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public final class PeriodRules {
    private PeriodRules() {
    }

    public static PeriodConfiguration validateForCreate(PeriodConfiguration request) {
        PeriodConfiguration normalized = normalizeCommon(request);
        validateSchedule(normalized);
        validateDatesForType(normalized);
        return normalized;
    }

    public static PeriodConfiguration validateAndNormalizeUpdate(
            PeriodConfiguration current,
            PeriodConfiguration request,
            Instant now,
            ZoneId businessZone) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(businessZone, "businessZone");
        PeriodConfiguration normalized = normalizeCommon(request);
        boolean typeChanged = current.topicType() != normalized.topicType();

        if (now.isBefore(current.lecturerStart())) {
            if (typeChanged) {
                normalized = normalizeDatesAfterTypeChange(normalized);
            } else {
                validateDirectDateChanges(current, normalized, now, businessZone);
            }
        } else {
            requireEqual(current.name(), normalized.name(), "Period name is locked");
            requireEqual(current.topicType(), normalized.topicType(), "Period type is locked");
            requireEqual(current.lecturerStart(), normalized.lecturerStart(),
                    "Lecturer start is locked");
            requireEqual(current.studentStart(), normalized.studentStart(),
                    "Student start is locked");
            validateEndExtension(current.lecturerEnd(), normalized.lecturerEnd(), now,
                    "Lecturer window cannot be shortened or reopened");
            validateEndExtension(current.studentEnd(), normalized.studentEnd(), now,
                    "Student window cannot be shortened or reopened");
            validateDirectDateChanges(current, normalized, now, businessZone);
        }

        validateSchedule(normalized);
        validateDatesForType(normalized);
        return normalized;
    }

    public static RegistrationState stateAt(PeriodConfiguration period, Instant now) {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(now, "now");
        if (now.isBefore(period.lecturerStart())) {
            return RegistrationState.BEFORE_LECTURER;
        }
        if (now.isBefore(period.lecturerEnd())) {
            return RegistrationState.LECTURER_OPEN;
        }
        if (now.isBefore(period.studentStart())) {
            return RegistrationState.BETWEEN_WINDOWS;
        }
        if (now.isBefore(period.studentEnd())) {
            return RegistrationState.STUDENT_OPEN;
        }
        return RegistrationState.REGISTRATION_CLOSED;
    }

    private static PeriodConfiguration normalizeCommon(PeriodConfiguration request) {
        Objects.requireNonNull(request, "request");
        String name = request.name() == null ? null : request.name().strip();
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Period name is required");
        }
        Objects.requireNonNull(request.topicType(), "topicType");
        Objects.requireNonNull(request.lecturerStart(), "lecturerStart");
        Objects.requireNonNull(request.lecturerEnd(), "lecturerEnd");
        Objects.requireNonNull(request.studentStart(), "studentStart");
        Objects.requireNonNull(request.studentEnd(), "studentEnd");
        return new PeriodConfiguration(
                name,
                request.topicType(),
                request.lecturerStart(),
                request.lecturerEnd(),
                request.studentStart(),
                request.studentEnd(),
                request.reviewerScoreDeadline(),
                request.councilReportDate());
    }

    private static PeriodConfiguration normalizeDatesAfterTypeChange(PeriodConfiguration request) {
        return switch (request.topicType()) {
            case COURSE, NCKH -> request.withDates(null, null);
            case TLCN -> request.withDates(request.reviewerScoreDeadline(), null);
            case KLTN -> request;
        };
    }

    private static void validateSchedule(PeriodConfiguration period) {
        if (!period.lecturerStart().isBefore(period.lecturerEnd())
                || period.lecturerEnd().isAfter(period.studentStart())
                || !period.studentStart().isBefore(period.studentEnd())) {
            throw new IllegalArgumentException("Registration windows are out of order");
        }
    }

    private static void validateDatesForType(PeriodConfiguration period) {
        boolean reviewerPresent = period.reviewerScoreDeadline() != null;
        boolean councilPresent = period.councilReportDate() != null;
        boolean valid = switch (period.topicType()) {
            case COURSE, NCKH -> !reviewerPresent && !councilPresent;
            case TLCN -> reviewerPresent && !councilPresent;
            case KLTN -> reviewerPresent && councilPresent;
        };
        if (!valid) {
            throw new IllegalArgumentException("Conditional dates do not match the period type");
        }
    }

    private static void validateDirectDateChanges(
            PeriodConfiguration current,
            PeriodConfiguration request,
            Instant now,
            ZoneId businessZone) {
        LocalDate today = now.atZone(businessZone).toLocalDate();
        validateDateExtension(
                current.reviewerScoreDeadline(),
                request.reviewerScoreDeadline(),
                today,
                "Reviewer score deadline cannot be shortened or reopened");
        validateDateExtension(
                current.councilReportDate(),
                request.councilReportDate(),
                today,
                "Council report date cannot be shortened or reopened");
    }

    private static void validateDateExtension(
            LocalDate current,
            LocalDate request,
            LocalDate today,
            String message) {
        if (Objects.equals(current, request)) {
            return;
        }
        if (current == null
                || request == null
                || !request.isAfter(current)
                || !today.isBefore(current)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void validateEndExtension(
            Instant current,
            Instant request,
            Instant now,
            String message) {
        if (Objects.equals(current, request)) {
            return;
        }
        if (!request.isAfter(current) || !now.isBefore(current)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireEqual(Object current, Object request, String message) {
        if (!Objects.equals(current, request)) {
            throw new IllegalArgumentException(message);
        }
    }

    public enum TopicType {
        COURSE,
        NCKH,
        TLCN,
        KLTN
    }

    public enum RegistrationState {
        BEFORE_LECTURER,
        LECTURER_OPEN,
        BETWEEN_WINDOWS,
        STUDENT_OPEN,
        REGISTRATION_CLOSED
    }

    public record PeriodConfiguration(
            String name,
            TopicType topicType,
            Instant lecturerStart,
            Instant lecturerEnd,
            Instant studentStart,
            Instant studentEnd,
            LocalDate reviewerScoreDeadline,
            LocalDate councilReportDate) {

        PeriodConfiguration withDates(
                LocalDate reviewerDeadline,
                LocalDate councilDate) {
            return new PeriodConfiguration(
                    name,
                    topicType,
                    lecturerStart,
                    lecturerEnd,
                    studentStart,
                    studentEnd,
                    reviewerDeadline,
                    councilDate);
        }
    }
}

package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.config.TimeConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.service.PeriodRules.PeriodConfiguration;
import vn.edu.topicregistration.service.PeriodRules.RegistrationState;
import vn.edu.topicregistration.service.PeriodRules.TopicType;
import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;

@Service
@Profile("!test")
public class PeriodService {
    private final PeriodTransactionService transactions;
    private final RegistrationPeriodRepository periods;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public PeriodService(
            PeriodTransactionService transactions,
            RegistrationPeriodRepository periods,
            AuthorizationPolicy authorization,
            Clock clock) {
        this.transactions = transactions;
        this.periods = periods;
        this.authorization = authorization;
        this.clock = clock;
    }

    public PeriodView create(PeriodConfiguration request) {
        ActorIdentity dean = currentDean();
        RegistrationPeriodEntity saved = transactions.create(dean.facultyId(), context -> {
            PeriodConfiguration valid = validateCreate(request, context.now());
            return new RegistrationPeriodEntity(
                    context.faculty(),
                    valid.name(),
                    valid.topicType().name(),
                    valid.lecturerStart(),
                    valid.lecturerEnd(),
                    valid.studentStart(),
                    valid.studentEnd(),
                    valid.reviewerScoreDeadline(),
                    valid.councilReportDate());
        });
        return toView(saved, clock.instant());
    }

    public PeriodView update(long periodId, long expectedVersion, PeriodConfiguration request) {
        return transactions.mutate(periodId, expectedVersion, context -> {
            RegistrationPeriodEntity period = context.period();
            PeriodConfiguration valid = validateUpdate(
                    toConfiguration(period), request, context.now());
            period.update(
                    valid.name(),
                    valid.topicType().name(),
                    valid.lecturerStart(),
                    valid.lecturerEnd(),
                    valid.studentStart(),
                    valid.studentEnd(),
                    valid.reviewerScoreDeadline(),
                    valid.councilReportDate());
            periods.flush();
            return toView(period, context.now());
        });
    }

    @Transactional(readOnly = true)
    public List<PeriodView> listForCurrentDean() {
        ActorIdentity dean = currentDean();
        Instant now = clock.instant();
        return periods.findAllByFacultyIdOrderByLecturerStartDesc(dean.facultyId()).stream()
                .map(period -> toView(period, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public PeriodView getForCurrentDean(long periodId) {
        ActorIdentity dean = currentDean();
        RegistrationPeriodEntity period = periods
                .findByIdAndFacultyId(periodId, dean.facultyId())
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.RESOURCE_NOT_FOUND));
        return toView(period, clock.instant());
    }

    private ActorIdentity currentDean() {
        ActorIdentity actor = authorization.requireRole(RoleCode.DEAN);
        if (actor.facultyId() == null) {
            throw new AccessDeniedException("Dean faculty scope is missing");
        }
        return actor;
    }

    private PeriodConfiguration validateCreate(PeriodConfiguration request, Instant now) {
        try {
            return PeriodRules.validateForCreate(request, now, TimeConfig.BUSINESS_ZONE);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    private PeriodConfiguration validateUpdate(
            PeriodConfiguration current,
            PeriodConfiguration request,
            Instant now) {
        try {
            return PeriodRules.validateAndNormalizeUpdate(
                    current, request, now, TimeConfig.BUSINESS_ZONE);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
    }

    private PeriodConfiguration toConfiguration(RegistrationPeriodEntity period) {
        try {
            return new PeriodConfiguration(
                    period.getName(),
                    TopicType.valueOf(period.getTopicType()),
                    period.getLecturerStart(),
                    period.getLecturerEnd(),
                    period.getStudentStart(),
                    period.getStudentEnd(),
                    period.getReviewerScoreDeadline(),
                    period.getCouncilReportDate());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
    }

    private PeriodView toView(RegistrationPeriodEntity period, Instant now) {
        PeriodConfiguration configuration = toConfiguration(period);
        return new PeriodView(
                period.getId(),
                period.getFaculty().getId(),
                configuration.name(),
                configuration.topicType(),
                configuration.lecturerStart(),
                configuration.lecturerEnd(),
                configuration.studentStart(),
                configuration.studentEnd(),
                configuration.reviewerScoreDeadline(),
                configuration.councilReportDate(),
                period.getVersion(),
                PeriodRules.stateAt(configuration, now));
    }

    public record PeriodView(
            Long id,
            Long facultyId,
            String name,
            TopicType topicType,
            Instant lecturerStart,
            Instant lecturerEnd,
            Instant studentStart,
            Instant studentEnd,
            LocalDate reviewerScoreDeadline,
            LocalDate councilReportDate,
            long version,
            RegistrationState state) {
    }
}

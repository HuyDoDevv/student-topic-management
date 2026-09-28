package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.UserAccountEntity;
import vn.edu.topicregistration.repository.UserAccountRepository;
import vn.edu.topicregistration.entity.RegistrationPeriodEntity;
import vn.edu.topicregistration.repository.RegistrationPeriodRepository;
import vn.edu.topicregistration.exception.ApplicationErrorCode;
import vn.edu.topicregistration.exception.ApplicationException;
import vn.edu.topicregistration.security.AuthorizationPolicy;
import vn.edu.topicregistration.entity.TopicEntity;
import vn.edu.topicregistration.repository.TopicRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.security.AuthorizationPolicy.ActorIdentity;

@Service
@Profile("!test")
public class TopicModerationService {
    private final RegistrationPeriodRepository periods;
    private final TopicRepository topics;
    private final UserAccountRepository accounts;
    private final AuthorizationPolicy authorization;
    private final Clock clock;

    public TopicModerationService(
            RegistrationPeriodRepository periods,
            TopicRepository topics,
            UserAccountRepository accounts,
            AuthorizationPolicy authorization,
            Clock clock) {
        this.periods = periods;
        this.topics = topics;
        this.accounts = accounts;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ModerationResult hide(long topicId, long expectedVersion, String requestedReason) {
        String reason = normalizeReason(requestedReason);
        long periodId = topics.findPeriodIdByTopicId(topicId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        RegistrationPeriodEntity period = periods.findByIdForUpdate(periodId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        ActorIdentity admin = authorization.requireEmergencyAdmin();

        TopicEntity topic = topics.findByIdForUpdate(topicId)
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.RESOURCE_NOT_FOUND));
        if (!topic.getPeriod().getId().equals(period.getId())) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT);
        }
        if (topic.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("Topic version is stale");
        }

        LockedAccounts locked = lockAccounts(admin.accountId(), topic.getAuthor().getId());
        topic.hide(locked.admin(), reason, clock.instant());
        locked.author().disable();
        topics.flush();
        accounts.flush();
        return new ModerationResult(topic.getId(), period.getId());
    }

    private LockedAccounts lockAccounts(long adminId, long authorId) {
        List<Long> ids = new ArrayList<>(List.of(adminId, authorId));
        ids.sort(Comparator.naturalOrder());
        UserAccountEntity first = requiredLockedAccount(ids.get(0));
        UserAccountEntity second = requiredLockedAccount(ids.get(1));
        UserAccountEntity admin = first.getId() == adminId ? first : second;
        UserAccountEntity author = first.getId() == authorId ? first : second;
        return new LockedAccounts(admin, author);
    }

    private UserAccountEntity requiredLockedAccount(long accountId) {
        return accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.CONFLICT));
    }

    private String normalizeReason(String requestedReason) {
        String reason = requestedReason == null ? "" : requestedReason.strip();
        if (reason.isEmpty() || reason.length() > 1000) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED);
        }
        return reason;
    }

    private record LockedAccounts(
            UserAccountEntity admin,
            UserAccountEntity author) {
    }

    public record ModerationResult(long topicId, long periodId) {
    }
}

package vn.edu.topicregistration.topics.domain;

import java.util.Objects;

import vn.edu.topicregistration.topics.domain.TopicRules.TopicStatus;

public final class TopicReviewRules {
    private TopicReviewRules() {
    }

    public static TopicStatus review(TopicStatus currentStatus, ReviewDecision decision) {
        Objects.requireNonNull(currentStatus, "currentStatus");
        Objects.requireNonNull(decision, "decision");
        if (currentStatus != TopicStatus.PENDING_APPROVAL) {
            throw new InvalidTopicReviewStateException();
        }
        return decision == ReviewDecision.APPROVE
                ? TopicStatus.APPROVED
                : TopicStatus.DRAFT;
    }

    public static TopicStatus publish(TopicStatus currentStatus) {
        Objects.requireNonNull(currentStatus, "currentStatus");
        if (currentStatus != TopicStatus.APPROVED) {
            throw new InvalidTopicReviewStateException();
        }
        return TopicStatus.PUBLISHED;
    }

    public enum ReviewDecision {
        APPROVE,
        REJECT
    }

    public static final class InvalidTopicReviewStateException extends RuntimeException {
        public InvalidTopicReviewStateException() {
            super("Topic state does not allow this review operation");
        }
    }
}

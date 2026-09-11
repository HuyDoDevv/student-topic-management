package vn.edu.topicregistration.groups.application;

public interface ActiveRegistrationQuery {
    boolean existsForGroup(long periodId, long groupId);
}

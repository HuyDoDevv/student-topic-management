package vn.edu.topicregistration.registrations.application;

import java.util.EnumSet;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.edu.topicregistration.groups.application.ActiveRegistrationQuery;
import vn.edu.topicregistration.registrations.persistence.RegistrationRepository;
import vn.edu.topicregistration.registrations.persistence.RegistrationStatus;

@Repository
@Profile("!preview & !test")
public class ActiveRegistrationQueryAdapter implements ActiveRegistrationQuery {
    private static final EnumSet<RegistrationStatus> ACTIVE_STATUSES = EnumSet.of(
            RegistrationStatus.PENDING,
            RegistrationStatus.ACCEPTED);

    private final RegistrationRepository registrations;

    public ActiveRegistrationQueryAdapter(RegistrationRepository registrations) {
        this.registrations = registrations;
    }

    @Override
    public boolean existsForGroup(long periodId, long groupId) {
        return registrations.existsByPeriodIdAndGroupIdAndStatusIn(
                periodId,
                groupId,
                ACTIVE_STATUSES);
    }
}

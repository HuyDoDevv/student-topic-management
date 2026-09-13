package vn.edu.topicregistration.registrations.application;

import java.util.EnumSet;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import vn.edu.topicregistration.groups.application.ActiveRegistrationQuery;
import vn.edu.topicregistration.groups.application.ActiveRegistrationQuery.ActiveRegistration;
import vn.edu.topicregistration.groups.application.ActiveRegistrationQuery.ActiveRegistrationStatus;
import vn.edu.topicregistration.registrations.persistence.RegistrationEntity;
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

    @Override
    public Optional<ActiveRegistration> findForGroup(long periodId, long groupId) {
        return registrations
                .findFirstByPeriodIdAndGroupIdAndStatusInOrderBySubmittedAtDescIdDesc(
                        periodId,
                        groupId,
                        ACTIVE_STATUSES)
                .map(this::toActiveRegistration);
    }

    private ActiveRegistration toActiveRegistration(RegistrationEntity registration) {
        ActiveRegistrationStatus status = switch (registration.getStatus()) {
            case PENDING -> ActiveRegistrationStatus.PENDING;
            case ACCEPTED -> ActiveRegistrationStatus.ACCEPTED;
            default -> throw new IllegalStateException(
                    "Inactive registration returned by active registration query");
        };
        return new ActiveRegistration(
                registration.getId(),
                registration.getTopic().getTitle(),
                status);
    }
}

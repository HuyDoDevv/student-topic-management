package vn.edu.topicregistration.service;

import vn.edu.topicregistration.entity.RegistrationEntity;
import vn.edu.topicregistration.repository.RegistrationRepository;
import vn.edu.topicregistration.entity.RegistrationStatus;

import java.util.EnumSet;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
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
    public Optional<ActiveRegistrationQuery.ActiveRegistration> findForGroup(
            long periodId,
            long groupId) {
        return registrations
                .findFirstByPeriodIdAndGroupIdAndStatusInOrderBySubmittedAtDescIdDesc(
                        periodId,
                        groupId,
                        ACTIVE_STATUSES)
                .map(this::toActiveRegistration);
    }

    private ActiveRegistrationQuery.ActiveRegistration toActiveRegistration(
            RegistrationEntity registration) {
        ActiveRegistrationQuery.ActiveRegistrationStatus status = switch (registration.getStatus()) {
            case PENDING -> ActiveRegistrationQuery.ActiveRegistrationStatus.PENDING;
            case ACCEPTED -> ActiveRegistrationQuery.ActiveRegistrationStatus.ACCEPTED;
            default -> throw new IllegalStateException(
                    "Inactive registration returned by active registration query");
        };
        return new ActiveRegistrationQuery.ActiveRegistration(
                registration.getId(),
                registration.getTopic().getTitle(),
                status);
    }
}

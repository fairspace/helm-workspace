package io.fairspace.saturn.config.health;

import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import io.fairspace.saturn.services.views.ViewStoreClientFactory;

@Component
public class FairspaceReadinessStateHealthIndicator extends ReadinessStateHealthIndicator {

    private static final int CONNECTION_TIMEOUT = 1;

    private final ViewStoreClientFactory viewStoreClientFactory;

    public FairspaceReadinessStateHealthIndicator(
            ApplicationAvailability availability, @Nullable ViewStoreClientFactory viewStoreClientFactory) {
        super(availability);
        this.viewStoreClientFactory = viewStoreClientFactory;
    }

    @Override
    protected AvailabilityState getState(ApplicationAvailability applicationAvailability) {
        return isConnectionValid() ? ReadinessState.ACCEPTING_TRAFFIC : ReadinessState.REFUSING_TRAFFIC;
    }

    private boolean isConnectionValid() {
        if (viewStoreClientFactory != null) {
            try (var connection = viewStoreClientFactory.dataSource.getConnection()) {
                return connection.isValid(CONNECTION_TIMEOUT);
            } catch (Exception e) {
                return false;
            }
        } else {
            return true;
        }
    }
}

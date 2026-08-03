package com.booking.azure.infrastructure.adapter.out.graph;

import com.booking.azure.application.port.out.GraphApiRequest;
import com.booking.azure.domain.model.vo.TenantId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Collections;

/**
 * Mock Adapter for GraphApiRequest, active only in the 'feature' profile.
 * Replaces real Graph API calls with logs and dummy data to save costs.
 */
@Slf4j
@Component
@Profile("feature")
public class MockGraphApiAdapter implements GraphApiRequest {

    @Override
    public <T> T get(TenantId tenantId, String path, Class<T> responseType) {
        log.info("Mocking Graph GET to {} for tenant {}", path, tenantId);
        return createDummyInstance(responseType);
    }

    @Override
    public <T> T post(TenantId tenantId, String path, Object body, Class<T> responseType) {
        log.info("Mocking Graph POST to {} for tenant {} with body: {}", path, tenantId, body);
        return createDummyInstance(responseType);
    }

    @Override
    public <T> T patch(TenantId tenantId, String path, Object body, Class<T> responseType) {
        log.info("Mocking Graph PATCH to {} for tenant {} with body: {}", path, tenantId, body);
        return createDummyInstance(responseType);
    }

    @Override
    public void delete(TenantId tenantId, String path) {
        log.info("Mocking Graph DELETE to {} for tenant {}", path, tenantId);
    }

    private <T> T createDummyInstance(Class<T> type) {
        try {
            // For simple records/POJOs, try default constructor. 
            // In a real scenario, you'd use Jackson or a dummy factory to populate fields.
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.warn("Could not create dummy instance of {}, returning null", type.getName());
            return null;
        }
    }
}

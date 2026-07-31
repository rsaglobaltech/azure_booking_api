package com.booking.azure.infrastructure.adapter.out.graph;

import com.booking.azure.domain.port.out.GraphApiRequest;
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
    public <T> T get(String path, Class<T> antwortTyp) {
        log.info("Mocking Graph GET to: {}", path);
        return createDummyInstance(antwortTyp);
    }

    @Override
    public <T> T post(String path, Object body, Class<T> antwortTyp) {
        log.info("Mocking Graph POST to: {} with body: {}", path, body);
        return createDummyInstance(antwortTyp);
    }

    @Override
    public <T> T patch(String path, Object body, Class<T> antwortTyp) {
        log.info("Mocking Graph PATCH to: {} with body: {}", path, body);
        return createDummyInstance(antwortTyp);
    }

    @Override
    public void delete(String path) {
        log.info("Mocking Graph DELETE to: {}", path);
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

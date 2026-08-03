package com.booking.azure.infrastructure.adapter.out.graph;

import com.booking.azure.domain.model.vo.TenantId;
import com.booking.azure.infrastructure.config.GraphApiProperties;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acquires Microsoft Graph access tokens, one directory at a time.
 *
 * <h2>Multi-tenancy</h2>
 *
 * This platform holds a single application registration — one client id, one
 * secret. Each customer organisation lives in its own Entra ID tenant and grants
 * that registration admin consent when it signs up. A token is therefore
 * acquired <b>per tenant</b>: same credentials, different authority
 * ({@code https://login.microsoftonline.com/{tenantId}/}).
 *
 * Both the MSAL client and the token are cached <b>per tenant</b>. A single
 * shared cache would hand one organisation a token issued for another's
 * directory — a call that fails at best and reaches the wrong calendar at worst.
 *
 * <h2>Token lifetime</h2>
 *
 * A cached token is reused while it still has more than five minutes left. The
 * margin exists so a token cannot expire between the check and the call it
 * authenticates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile({"integration", "prod", "test"})
public class GraphAuthService {

    /** Reuse a token only while this much validity remains. */
    private static final long EXPIRY_MARGIN_MILLIS = 300_000;

    private final GraphApiProperties properties;

    private final Map<TenantId, ConfidentialClientApplication> clients = new ConcurrentHashMap<>();
    private final Map<TenantId, CachedToken> tokens = new ConcurrentHashMap<>();

    private record CachedToken(String value, long expiresAt) {
        boolean isUsable(long now) {
            return now < expiresAt - EXPIRY_MARGIN_MILLIS;
        }
    }

    /**
     * A valid access token for the given directory.
     *
     * @param tenantId the Entra ID tenant the target agency belongs to
     * @throws RuntimeException if no token could be acquired
     */
    public String getAccessToken(TenantId tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required to acquire a token");
        }

        CachedToken cached = tokens.get(tenantId);
        if (cached != null && cached.isUsable(System.currentTimeMillis())) {
            return cached.value();
        }

        try {
            ClientCredentialParameters parameters = ClientCredentialParameters
                    .builder(Collections.singleton(properties.getScope()))
                    .build();

            CompletableFuture<IAuthenticationResult> future =
                    clientFor(tenantId).acquireToken(parameters);
            IAuthenticationResult result = future.get();

            tokens.put(tenantId, new CachedToken(
                    result.accessToken(), result.expiresOnDate().getTime()));

            log.debug("Access token acquired for tenant {}, valid until {}",
                    tenantId, result.expiresOnDate());
            return result.accessToken();

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while acquiring an access token for tenant " + tenantId, ex);
        } catch (Exception ex) {
            log.error("Could not acquire an Azure AD access token for tenant {}: {}",
                    tenantId, ex.getMessage(), ex);
            throw new IllegalStateException(
                    "Could not acquire an access token for tenant " + tenantId, ex);
        }
    }

    /**
     * The MSAL client for one directory, built once and reused.
     *
     * Building it is expensive and it is thread-safe once built, hence the
     * cache. The authority carries the tenant — that is what makes the token
     * apply to that organisation's calendars and no one else's.
     */
    private ConfidentialClientApplication clientFor(TenantId tenantId) {
        return clients.computeIfAbsent(tenantId, tenant -> {
            try {
                String authority = "https://login.microsoftonline.com/" + tenant.value() + "/";
                ConfidentialClientApplication client = ConfidentialClientApplication.builder(
                                properties.getClientId(),
                                ClientCredentialFactory.createFromSecret(properties.getClientSecret()))
                        .authority(authority)
                        .build();
                log.info("Azure AD client created for tenant {}", tenant);
                return client;
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Could not create an Azure AD client for tenant " + tenant, ex);
            }
        });
    }
}

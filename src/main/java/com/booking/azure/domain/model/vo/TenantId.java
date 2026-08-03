package com.booking.azure.domain.model.vo;

/**
 * The Entra ID (Azure AD) directory an agency belongs to.
 *
 * <h2>Why this is per agency and not per deployment</h2>
 *
 * Each customer organisation lives in its own Entra ID tenant. This platform
 * holds one application registration, and each organisation's administrator
 * grants it admin consent when they sign up. A token is then acquired
 * <b>per tenant</b>: same client id and secret, different authority.
 *
 * Treating the tenant as one global setting works only while every agency
 * happens to sit in the same directory. The moment a second organisation joins,
 * a booking would be written with a token issued for somebody else's directory
 * — it would fail, or worse, reach the wrong calendar.
 */
public record TenantId(String value) {

    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        value = value.trim();
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

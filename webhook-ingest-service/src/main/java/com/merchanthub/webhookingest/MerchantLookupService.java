package com.merchanthub.webhookingest;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves a shop API key to a merchant id using the same
 * {@code resolve_merchant_by_api_key} DB function the backend relies on
 * (see V1__init_schema.sql), keeping merchant resolution logic in one place.
 */
@ApplicationScoped
public class MerchantLookupService {

    private final AgroalDataSource dataSource;

    public MerchantLookupService(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<UUID> findMerchantIdByApiKey(String apiKey) {
        String sql = "select id from resolve_merchant_by_api_key(?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, apiKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject("id", UUID.class)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new RuntimeException("Merchant lookup failed", e);
        }
    }
}

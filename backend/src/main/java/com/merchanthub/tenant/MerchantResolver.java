package com.merchanthub.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MerchantResolver {

    private final JdbcTemplate jdbc;

    public MerchantResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record MerchantRow(UUID id, UUID authUserId, String name, String email, String shopApiKey, String passwordHash) {}

    public Optional<MerchantRow> findByAuthUid(UUID authUid) {
        return jdbc.query(
                "select id, name, email, shop_api_key from resolve_merchant_by_auth_uid(?)",
                rs -> rs.next()
                        ? Optional.of(new MerchantRow(
                            rs.getObject("id", UUID.class), authUid,
                            rs.getString("name"), rs.getString("email"), rs.getString("shop_api_key"), null))
                        : Optional.empty(),
                authUid);
    }

    public Optional<MerchantRow> findByEmail(String email) {
        return jdbc.query(
                "select id, auth_user_id, name, email, shop_api_key, password_hash from resolve_merchant_by_email(?)",
                rs -> rs.next()
                        ? Optional.of(new MerchantRow(
                            rs.getObject("id", UUID.class), rs.getObject("auth_user_id", UUID.class),
                            rs.getString("name"), rs.getString("email"), rs.getString("shop_api_key"),
                            rs.getString("password_hash")))
                        : Optional.empty(),
                email);
    }

    public Optional<MerchantRow> findByApiKey(String apiKey) {
        return jdbc.query(
                "select id, auth_user_id, name, email, shop_api_key from resolve_merchant_by_api_key(?)",
                rs -> rs.next() ? Optional.of(mapFull(rs)) : Optional.empty(),
                apiKey);
    }

    public record SyncTarget(UUID merchantId, String shopApiKey) {}

    public List<SyncTarget> listForSync() {
        return jdbc.query(
                "select id, shop_api_key from list_merchants_for_sync()",
                (rs, n) -> new SyncTarget(rs.getObject("id", UUID.class), rs.getString("shop_api_key")));
    }

    /** Creates (or upserts) a merchant for a freshly seen auth user. Returns its id. */
    public UUID provision(UUID authUid, String name, String email) {
        return jdbc.queryForObject("select provision_merchant(?, ?, ?)", UUID.class, authUid, name, email);
    }

    /** Registers a new merchant with a password (POST /api/auth/register). Returns its id. */
    public UUID registerWithPassword(UUID authUid, String name, String email, String passwordHash) {
        return jdbc.queryForObject("select register_merchant(?, ?, ?, ?)", UUID.class,
                authUid, name, email, passwordHash);
    }

    private static MerchantRow mapFull(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MerchantRow(
                rs.getObject("id", UUID.class),
                rs.getObject("auth_user_id", UUID.class),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("shop_api_key"),
                null);
    }
}

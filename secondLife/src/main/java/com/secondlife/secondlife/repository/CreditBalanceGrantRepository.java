package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.enums.CreditType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CreditBalanceGrantRepository {
    private final JdbcTemplate jdbcTemplate;

    /** The upsert locks the balance row and returns the quantity for the matching ledger entry. */
    public long grant(UUID userId, CreditType type, int quantity) {
        Long balance = jdbcTemplate.queryForObject("""
                INSERT INTO credit_balances (id, user_id, credit_type, quantity, version, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 0, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, credit_type) DO UPDATE
                SET quantity = credit_balances.quantity + EXCLUDED.quantity,
                    version = credit_balances.version + 1,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING quantity
                """, Long.class, userId, type.name(), quantity);
        return balance;
    }
}

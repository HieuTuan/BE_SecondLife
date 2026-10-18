package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CreditBalance;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.ConflictException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditBalanceRepository extends JpaRepository<CreditBalance, UUID> {
    List<CreditBalance> findByUserId(UUID userId);

    @Transactional
    default long consumeOne(UUID userId, CreditType type) {
        return consumeOneIfAvailable(userId, type)
                .orElseThrow(() -> new ConflictException("Insufficient " + type + " credits"));
    }

    /** Conditional UPDATE locks the row and cannot overdraw, even against concurrent grants. */
    @Transactional
    @Query(value = """
            WITH consumed AS (
                UPDATE credit_balances SET quantity = quantity - 1, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId AND credit_type = :#{#type.name()} AND quantity >= 1
                RETURNING quantity
            )
            SELECT quantity FROM consumed
            """, nativeQuery = true)
    Optional<Long> consumeOneIfAvailable(@Param("userId") UUID userId, @Param("type") CreditType type);

    /** The upsert locks the balance row and returns the quantity for the matching ledger entry. */
    @Transactional
    @Query(value = """
            WITH granted AS (
                INSERT INTO credit_balances (id, user_id, credit_type, quantity, version, updated_at)
                VALUES (gen_random_uuid(), :userId, :#{#type.name()}, :quantity, 0, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, credit_type) DO UPDATE
                SET quantity = credit_balances.quantity + EXCLUDED.quantity,
                    version = credit_balances.version + 1,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING quantity
            )
            SELECT quantity FROM granted
            """, nativeQuery = true)
    long grant(@Param("userId") UUID userId, @Param("type") CreditType type, @Param("quantity") int quantity);
}

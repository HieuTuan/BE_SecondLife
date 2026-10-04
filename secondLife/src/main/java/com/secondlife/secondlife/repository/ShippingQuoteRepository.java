package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.ShippingQuote;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ShippingQuoteRepository extends JpaRepository<ShippingQuote,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select q from ShippingQuote q where q.id=:id")
    Optional<ShippingQuote> findByIdForUpdate(@Param("id") UUID id);
}

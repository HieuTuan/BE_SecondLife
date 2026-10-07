package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id=:id")
    java.util.Optional<Order> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    java.util.Optional<Order> findByBuyerIdAndRequestId(UUID buyerId, UUID requestId);
    Page<Order> findByBuyerId(UUID buyerId, Pageable pageable);
    Page<Order> findBySellerId(UUID sellerId, Pageable pageable);
}

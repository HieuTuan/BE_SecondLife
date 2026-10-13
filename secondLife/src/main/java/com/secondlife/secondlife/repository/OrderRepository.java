package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @org.springframework.data.jpa.repository.Query("""
        select o.id as id, o.shippingDeliveredAt as deliveredAt from Order o
        where o.status = :status and o.escrowStatus = :escrow and o.shippingDeliveredAt <= :cutoff
        order by o.shippingDeliveredAt, o.id
        """)
    java.util.List<DueSettlementOrder> findDueSettlementOrders(
            @org.springframework.data.repository.query.Param("status") com.secondlife.secondlife.enums.OrderStatus status,
            @org.springframework.data.repository.query.Param("escrow") com.secondlife.secondlife.enums.EscrowStatus escrow,
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff, Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
        select o.id as id, o.shippingDeliveredAt as deliveredAt from Order o
        where o.status = :status and o.escrowStatus = :escrow and o.shippingDeliveredAt <= :cutoff
        and (o.shippingDeliveredAt > :afterDelivery
             or (o.shippingDeliveredAt = :afterDelivery and o.id > :afterId))
        order by o.shippingDeliveredAt, o.id
        """)
    java.util.List<DueSettlementOrder> findDueSettlementOrdersAfter(
            @org.springframework.data.repository.query.Param("status") com.secondlife.secondlife.enums.OrderStatus status,
            @org.springframework.data.repository.query.Param("escrow") com.secondlife.secondlife.enums.EscrowStatus escrow,
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            @org.springframework.data.repository.query.Param("afterDelivery") java.time.Instant afterDelivery,
            @org.springframework.data.repository.query.Param("afterId") UUID afterId, Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id=:id")
    java.util.Optional<Order> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    java.util.Optional<Order> findByBuyerIdAndRequestId(UUID buyerId, UUID requestId);
    Page<Order> findByBuyerId(UUID buyerId, Pageable pageable);
    Page<Order> findBySellerId(UUID sellerId, Pageable pageable);
}

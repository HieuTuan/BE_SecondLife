package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.config.JpaRepositoryConfiguration.RefreshOperations;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, RefreshOperations {
    @Query("""
        select o.id as id, o.shippingDeliveredAt as deliveredAt from Order o
        where o.status = :status and o.escrowStatus = :escrow and o.shippingDeliveredAt <= :cutoff
        order by o.shippingDeliveredAt, o.id
        """)
    List<DueSettlementOrder> findDueSettlementOrders(
            @Param("status") OrderStatus status,
            @Param("escrow") EscrowStatus escrow,
            @Param("cutoff") Instant cutoff, Pageable pageable);

    @Query("""
        select o.id as id, o.shippingDeliveredAt as deliveredAt from Order o
        where o.status = :status and o.escrowStatus = :escrow and o.shippingDeliveredAt <= :cutoff
        and (o.shippingDeliveredAt > :afterDelivery
             or (o.shippingDeliveredAt = :afterDelivery and o.id > :afterId))
        order by o.shippingDeliveredAt, o.id
        """)
    List<DueSettlementOrder> findDueSettlementOrdersAfter(
            @Param("status") OrderStatus status,
            @Param("escrow") EscrowStatus escrow,
            @Param("cutoff") Instant cutoff,
            @Param("afterDelivery") Instant afterDelivery,
            @Param("afterId") UUID afterId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id=:id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);
    @Query("select o.post.id from Order o where o.id=:id")
    Optional<UUID> findPostId(@Param("id") UUID id);
    Optional<Order> findByBuyerIdAndRequestId(UUID buyerId, UUID requestId);
    @EntityGraph(attributePaths = "post")
    Page<Order> findByBuyerId(UUID buyerId, Pageable pageable);
    Page<Order> findByConfirmationDeadlineIsNotNull(Pageable pageable);
    Page<Order> findByConfirmationDeadlineIsNotNullAndStatus(OrderStatus status, Pageable pageable);

    @Query("""
        select o.id as id, o.confirmationDeadline as deadline from Order o
        where o.status=:status and o.escrowStatus=:escrow and o.sellerConfirmedAt is null
          and o.confirmationDeadline <= :cutoff and o.confirmationCancelAt is not null
        order by o.confirmationDeadline,o.id
        """)
    List<DueConfirmationOrder> findDueConfirmations(
        @Param("status") OrderStatus status,
        @Param("escrow") EscrowStatus escrow,
        @Param("cutoff") Instant cutoff, Pageable page);

    @Query("""
        select o.id as id, o.confirmationDeadline as deadline from Order o
        where o.status=:status and o.escrowStatus=:escrow and o.sellerConfirmedAt is null
          and o.confirmationDeadline <= :cutoff and o.confirmationCancelAt is not null
          and (o.confirmationDeadline > :afterDeadline or (o.confirmationDeadline=:afterDeadline and o.id > :afterId))
        order by o.confirmationDeadline,o.id
        """)
    List<DueConfirmationOrder> findDueConfirmationsAfter(
        @Param("status") OrderStatus status,
        @Param("escrow") EscrowStatus escrow,
        @Param("cutoff") Instant cutoff,
        @Param("afterDeadline") Instant afterDeadline,
        @Param("afterId") UUID afterId, Pageable page);
    @EntityGraph(attributePaths = "post")
    Page<Order> findBySellerId(UUID sellerId, Pageable pageable);

    interface DueConfirmationOrder {
        UUID getId();
        Instant getDeadline();
    }

    interface DueSettlementOrder {
        UUID getId();
        Instant getDeliveredAt();
    }

}

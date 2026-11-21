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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, RefreshOperations {
    // A linked inspection enriches its purchase order. Only unlinked inspections are separate rows.
    String FEED_SCOPE = """
            with candidates as (
                select o.id, i.id as inspection_id, o.post_id, o.buyer_id, o.seller_id, o.created_at,
                    false as pre_listing,
                    case
                        when o.status = 'COMPLETED' or o.escrow_status = 'RELEASED' then 'completed'
                        when o.status = 'CANCELLED' or o.escrow_status = 'REFUNDED' then 'cancelled'
                        when o.escrow_status = 'FROZEN' then 'disputed'
                        when o.status = 'DELIVERED' then 'delivered'
                        when o.status = 'SHIPPED' then 'shipping'
                        else 'awaiting_pickup'
                    end as stage
                from orders o
                left join lateral (
                    select id from inspection_orders i where i.order_id = o.id
                        and (i.buyer_id = :actor or i.seller_id = :actor)
                    order by i.created_at desc, i.id desc limit 1
                ) i on true
                where o.buyer_id = :actor or o.seller_id = :actor
                union all
                select i.id, i.id, i.post_id, i.buyer_id, i.seller_id, i.created_at,
                    i.inspection_scope = 'PRE_LISTING',
                    case
                        when i.status = 'COMPLETED' then 'completed'
                        when i.status in ('CANCELLED', 'PAYMENT_EXPIRED', 'FAILED', 'REJECTED') then 'cancelled'
                        when i.status = 'DISPUTED' then 'disputed'
                        when i.status = 'DELIVERING_TO_BUYER' then 'shipping'
                        else 'inspecting'
                    end
                from inspection_orders i
                where i.order_id is null and (i.buyer_id = :actor or i.seller_id = :actor)
            ), scoped as (
                select c.*, p.title, p.image_url, p.category_id, p.item_id, p.item_condition,
                    coalesce(nullif(b.full_name, ''), bu.email) as buyer_name,
                    coalesce(nullif(s.full_name, ''), su.email) as seller_name
                from candidates c
                join posts p on p.id = c.post_id
                left join users bu on bu.id = c.buyer_id
                left join users su on su.id = c.seller_id
                left join user_profiles b on b.user_id = c.buyer_id
                left join user_profiles s on s.user_id = c.seller_id
            ), matched as (
                select * from scoped
                where (:perspective = 'all' or (:perspective = 'buyer' and buyer_id = :actor)
                       or (:perspective = 'seller' and seller_id = :actor))
                  and (:q = '' or strpos(lower(cast(id as text)), :q) > 0
                       or strpos(lower(cast(inspection_id as text)), :q) > 0
                       or strpos(lower(title), :q) > 0 or strpos(lower(buyer_name), :q) > 0
                       or strpos(lower(seller_name), :q) > 0)
            )
            """;

    @Query(value = FEED_SCOPE + """
            select count(*) filter (where not pre_listing) as "allCount",
                   count(*) filter (where not pre_listing and stage = 'awaiting_pickup') as "awaitingPickup",
                   count(*) filter (where not pre_listing and stage = 'inspecting') as inspecting,
                   count(*) filter (where not pre_listing and stage = 'shipping') as shipping,
                   count(*) filter (where not pre_listing and stage = 'delivered') as delivered,
                   count(*) filter (where not pre_listing and stage = 'completed') as completed,
                   count(*) filter (where not pre_listing and stage = 'cancelled') as cancelled,
                   count(*) filter (where not pre_listing and stage = 'disputed') as disputed,
                   count(*) filter (where not pre_listing and inspection_id is not null) as "withInspection",
                   count(*) filter (where pre_listing) as "preListing"
            from matched
            """, nativeQuery = true)
    FeedCounts countFeedRows(@Param("actor") UUID actor, @Param("perspective") String perspective,
                            @Param("q") String search);

    @Query(value = FEED_SCOPE + """
            , page_rows as (
                select * from matched
                where (pre_listing and :filter = 'pre_listing')
                   or (not pre_listing and (:filter = 'all' or stage = :filter
                       or (:filter = 'with_inspection' and inspection_id is not null)))
                order by created_at desc, id desc limit :size offset :offset
            ), detail_row as (
                select * from scoped where id = cast(:detail as uuid) or inspection_id = cast(:detail as uuid)
                order by created_at desc, id desc limit 1
            )
            select id, inspection_id as "inspectionId", buyer_name as "buyerName", seller_name as "sellerName",
                   image_url as "postImageUrl", category_id as "categoryId", item_id as "itemId",
                   item_condition as "itemCondition", false as detail, created_at
            from page_rows
            union all
            select id, inspection_id, buyer_name, seller_name, image_url, category_id, item_id, item_condition,
                   true, created_at
            from detail_row
            order by detail, created_at desc, id desc
            """, nativeQuery = true)
    List<FeedRow> findFeedRows(@Param("actor") UUID actor, @Param("perspective") String perspective,
                               @Param("q") String search, @Param("filter") String filter, @Param("size") int size,
                               @Param("offset") long offset, @Param("detail") UUID detail);

    @Query("""
            select o from Order o join fetch o.post
            join fetch o.buyer b left join fetch b.profile
            join fetch o.seller s left join fetch s.profile
            left join fetch o.negotiation
            where o.id in :ids
            """)
    List<Order> findFeedOrdersByIds(@Param("ids") List<UUID> ids);

    interface FeedCounts {
        long getAllCount();
        long getAwaitingPickup();
        long getInspecting();
        long getShipping();
        long getDelivered();
        long getCompleted();
        long getCancelled();
        long getDisputed();
        long getWithInspection();
        long getPreListing();
    }

    interface FeedRow {
        UUID getId();
        UUID getInspectionId();
        String getBuyerName();
        String getSellerName();
        String getPostImageUrl();
        UUID getCategoryId();
        UUID getItemId();
        String getItemCondition();
        boolean isDetail();
    }

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
    @EntityGraph(attributePaths = {"post", "buyer", "seller", "negotiation"})
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
    @EntityGraph(attributePaths = {"post", "buyer", "seller", "negotiation"})
    Page<Order> findBySellerId(UUID sellerId, Pageable pageable);

    interface DueConfirmationOrder {
        UUID getId();
        Instant getDeadline();
    }

    interface DueSettlementOrder {
        UUID getId();
        Instant getDeliveredAt();
    }

    @EntityGraph(attributePaths = {"post", "buyer", "seller"})
    @Query("""
        select o from Order o
        where (:status is null or o.status = :status)
          and (:escrowStatus is null or o.escrowStatus = :escrowStatus)
          and (:orderId is null or o.id = :orderId)
          and (:search is null or :search = ''
               or lower(o.post.title) like lower(concat('%', :search, '%'))
               or lower(o.buyer.email) like lower(concat('%', :search, '%'))
               or lower(o.seller.email) like lower(concat('%', :search, '%'))
              )
        order by o.createdAt desc
    """)
    Page<Order> findAdminOrders(
        @Param("status") OrderStatus status,
        @Param("escrowStatus") EscrowStatus escrowStatus,
        @Param("orderId") UUID orderId,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("""
        select
            count(o) as totalOrders,
            count(case when o.status = com.secondlife.secondlife.enums.OrderStatus.CONFIRMED then 1 end) as confirmedOrders,
            count(case when o.status = com.secondlife.secondlife.enums.OrderStatus.PROCESSING then 1 end) as processingOrders,
            count(case when o.status = com.secondlife.secondlife.enums.OrderStatus.SHIPPED then 1 end) as shippingOrders,
            count(case when o.status = com.secondlife.secondlife.enums.OrderStatus.DELIVERED then 1 end) as deliveredOrders,
            count(case when o.status = com.secondlife.secondlife.enums.OrderStatus.COMPLETED then 1 end) as completedOrders,
            count(case when o.status = com.secondlife.secondlife.enums.OrderStatus.CANCELLED then 1 end) as cancelledOrders,
            count(case when o.escrowStatus = com.secondlife.secondlife.enums.EscrowStatus.FROZEN then 1 end) as disputedOrders,
            coalesce(sum(case when o.status != com.secondlife.secondlife.enums.OrderStatus.CANCELLED then o.finalPrice else 0 end), 0) as totalGmv,
            coalesce(sum(case when o.escrowStatus = com.secondlife.secondlife.enums.EscrowStatus.HELD then o.finalPrice else 0 end), 0) as heldEscrowAmount
        from Order o
    """)
    AdminOrderStatsProjection getAdminOrderStats();

    interface AdminOrderStatsProjection {
        long getTotalOrders();
        long getConfirmedOrders();
        long getProcessingOrders();
        long getShippingOrders();
        long getDeliveredOrders();
        long getCompletedOrders();
        long getCancelledOrders();
        long getDisputedOrders();
        BigDecimal getTotalGmv();
        BigDecimal getHeldEscrowAmount();
    }
}

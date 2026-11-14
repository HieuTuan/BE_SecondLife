package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.OrderResponseDTO;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.mapper.OrderMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderFeedService {
    private static final List<String> FILTERS = List.of("all", "awaiting_pickup", "inspecting", "shipping",
            "delivered", "completed", "cancelled", "disputed", "with_inspection");
    private static final List<String> PERSPECTIVES = List.of("all", "buyer", "seller");

    // A linked inspection enriches its purchase order. Only unlinked inspections are separate rows.
    private static final String FEED = """
            with candidates as (
                select o.id, i.id as inspection_id, o.post_id, o.buyer_id, o.seller_id, o.created_at,
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
    private static final String COUNTS = FEED + """
            select count(*), count(*) filter (where stage = 'awaiting_pickup'),
                count(*) filter (where stage = 'inspecting'), count(*) filter (where stage = 'shipping'),
                count(*) filter (where stage = 'delivered'), count(*) filter (where stage = 'completed'),
                count(*) filter (where stage = 'cancelled'), count(*) filter (where stage = 'disputed'),
                count(*) filter (where inspection_id is not null)
            from matched
            """;
    private static final String PAGE = FEED + """
            , page_rows as (
                select * from matched
                where :filter = 'all' or stage = :filter
                   or (:filter = 'with_inspection' and inspection_id is not null)
                order by created_at desc, id desc limit :size offset :offset
            ), detail_row as (
                select * from scoped where id = cast(:detail as uuid) or inspection_id = cast(:detail as uuid)
                order by created_at desc, id desc limit 1
            )
            select id, inspection_id, buyer_name, seller_name, image_url, category_id, item_id, item_condition,
                   false as detail, created_at from page_rows
            union all
            select id, inspection_id, buyer_name, seller_name, image_url, category_id, item_id, item_condition,
                   true, created_at from detail_row
            order by detail, created_at desc, id desc
            """;

    private final EntityManager entityManager;
    private final OrderBankPaymentService bankPayments;
    private final OrderMapper orderMapper;
    private final InspectionOrderMapper inspectionMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Feed read(UUID actor, String perspective, String filter, String query, int page, int size, UUID detail) {
        if (!PERSPECTIVES.contains(perspective) || !FILTERS.contains(filter)
                || page < 0 || size < 1 || size > 50 || query.length() > 150) {
            throw new BadRequestException("Bộ lọc đơn hàng không hợp lệ.");
        }
        String q = query.strip().replaceFirst("^#", "").toLowerCase(Locale.ROOT);
        Object[] countRow = (Object[]) entityManager.createNativeQuery(COUNTS)
                .setParameter("actor", actor).setParameter("perspective", perspective).setParameter("q", q)
                .getSingleResult();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < FILTERS.size(); i++) {
            counts.put(FILTERS.get(i), ((Number) countRow[i]).longValue());
        }
        long total = counts.get(filter);
        int totalPages = (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size);
        int currentPage = Math.min(page, Math.max(0, totalPages - 1));
        List<?> queryRows = entityManager.createNativeQuery(PAGE)
                .setParameter("actor", actor).setParameter("perspective", perspective).setParameter("q", q)
                .setParameter("filter", filter).setParameter("size", size)
                .setParameter("offset", (long) currentPage * size).setParameter("detail", detail)
                .getResultList();
        List<FeedRow> rows = queryRows.stream().map(row -> FeedRow.from((Object[]) row)).toList();

        var orderIds = rows.stream().filter(row -> !row.id().equals(row.inspectionId()))
                .map(FeedRow::id).distinct().toList();
        List<Order> orders = orderIds.isEmpty() ? List.of() : entityManager.createQuery("""
                select o from Order o join fetch o.post join fetch o.buyer join fetch o.seller
                left join fetch o.negotiation where o.id in :ids
                """, Order.class).setParameter("ids", orderIds).getResultList();
        var payments = bankPayments.detailsForOrders(orders);
        var orderData = orders.stream().collect(Collectors.toMap(Order::getId, order ->
                orderMapper.toResponse(order, payments.get(order.getId()), order.getFinalPrice().add(order.getShippingFee()))));

        var inspectionIds = rows.stream().map(FeedRow::inspectionId).filter(Objects::nonNull).distinct().toList();
        var inspectionData = inspectionIds.isEmpty() ? Map.<UUID, InspectionOrderResponse>of()
                : entityManager.createQuery("""
                        select i from InspectionOrder i join fetch i.post
                        left join fetch i.buyer b left join fetch b.profile
                        left join fetch i.seller s left join fetch s.profile
                        left join fetch i.inspector t left join fetch t.profile where i.id in :ids
                        """, InspectionOrder.class)
                .setParameter("ids", inspectionIds).getResultList().stream().map(inspectionMapper::toResponse)
                .collect(Collectors.toMap(InspectionOrderResponse::id, Function.identity()));

        List<Item> items = new ArrayList<>();
        Item detailItem = null;
        for (FeedRow row : rows) {
            var inspection = row.inspectionId() == null ? null : inspectionData.get(row.inspectionId());
            Item item = new Item(orderData.get(row.id()), inspection, row.buyerName(),
                    row.sellerName(), row.image(), row.categoryId(), row.itemId(), row.itemCondition());
            if (row.detail()) detailItem = item;
            else items.add(item);
        }
        return new Feed(items, detailItem, counts, currentPage, size, total, totalPages);
    }

    private record FeedRow(UUID id, UUID inspectionId, String buyerName, String sellerName, String image,
                           UUID categoryId, UUID itemId, String itemCondition, boolean detail) {
        static FeedRow from(Object[] row) {
            return new FeedRow((UUID) row[0], (UUID) row[1], (String) row[2], (String) row[3], (String) row[4],
                    (UUID) row[5], (UUID) row[6], (String) row[7], (Boolean) row[8]);
        }
    }

    public record Item(OrderResponseDTO order, InspectionOrderResponse inspection, String buyerName, String sellerName,
                       String postImageUrl, UUID categoryId, UUID itemId, String itemCondition) {}

    public record Feed(List<Item> items, Item detail, Map<String, Long> counts, int page, int size,
                       long totalElements, int totalPages) {}
}

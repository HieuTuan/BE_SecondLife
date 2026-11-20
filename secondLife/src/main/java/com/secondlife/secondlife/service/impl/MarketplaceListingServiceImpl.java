package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.MarketplaceListingFilter;
import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.PostMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository.ListingInspection;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.MarketplaceListingService;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("marketplaceListingService")
@Transactional(readOnly = true)
public class MarketplaceListingServiceImpl implements MarketplaceListingService {

    private final PostRepository posts;
    private final InspectionOrderRepository inspections;
    private final PostMapper postMapper;

    public MarketplaceListingServiceImpl(PostRepository posts, InspectionOrderRepository inspections, PostMapper postMapper) {
        this.posts = posts;
        this.inspections = inspections;
        this.postMapper = postMapper;
    }

    @Override
    public Page<MarketplaceListingResponse> list(UUID categoryId, UUID itemId, Pageable pageable) {
        return search(new MarketplaceListingFilter(categoryId, itemId, null, null, null, null, null, null, null),
                pageable);
    }

    @Override
    public Page<MarketplaceListingResponse> search(MarketplaceListingFilter criteria, Pageable pageable) {
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new BadRequestException("Giá tối thiểu phải nhỏ hơn hoặc bằng giá tối đa");
        }
        Specification<Post> filter = (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("user", JoinType.LEFT).fetch("profile", JoinType.LEFT);
            }
            return cb.equal(root.get("status"), "ACTIVE");
        };
        if (criteria.categoryId() != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("categoryId"), criteria.categoryId()));
        }
        if (criteria.itemId() != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("itemId"), criteria.itemId()));
        }
        if (hasText(criteria.q())) {
            String keyword = criteria.q().strip();
            filter = filter.and((root, query, cb) -> cb.or(
                    contains(cb, root.get("title"), keyword),
                    contains(cb, root.get("brand"), keyword),
                    contains(cb, root.get("description"), keyword)));
        }
        if (hasText(criteria.brand())) {
            String brand = criteria.brand().strip();
            String legacyPattern = wholeTermPattern(brand);
            filter = filter.and((root, query, cb) -> cb.or(
                    cb.equal(cb.lower(root.get("brand")), brand.toLowerCase(Locale.ROOT)),
                    cb.and(cb.isNull(root.get("brand")), cb.or(
                            matchesBrand(cb, root.get("title"), legacyPattern),
                            matchesBrand(cb, root.get("description"), legacyPattern)))));
        }
        if (criteria.condition() != null) {
            List<String> acceptedConditions = conditionValues(criteria.condition());
            filter = filter.and((root, query, cb) ->
                    cb.upper(cb.trim(root.get("itemCondition"))).in(acceptedConditions));
        }
        if (criteria.minPrice() != null) {
            filter = filter.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), criteria.minPrice()));
        }
        if (criteria.maxPrice() != null) {
            filter = filter.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), criteria.maxPrice()));
        }
        if (hasText(criteria.location())) {
            String location = criteria.location().strip();
            filter = filter.and((root, query, cb) -> contains(cb, root.get("sellerPickupAddress"), location));
        }
        if (Boolean.TRUE.equals(criteria.inspected())) {
            filter = filter.and(hasPublishedInspection());
        }
        Page<Post> page = posts.findAll(filter, pageable);
        if (page.isEmpty()) {
            return page.map(post -> toResponse(post, null));
        }
        var postIds = page.getContent().stream().map(Post::getId).toList();
        // Hydrate images in one query after database pagination.
        posts.findAllWithImagesByIdIn(postIds);
        var inspectionByPost = inspections.findLatestListingInspections(postIds).stream()
                .collect(Collectors.toMap(ListingInspection::getPostId, Function.identity()));
        return page.map(post -> toResponse(post, inspectionByPost.get(post.getId())));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static jakarta.persistence.criteria.Predicate contains(CriteriaBuilder cb, Expression<String> field, String value) {
        String pattern = value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return cb.like(cb.lower(field), "%" + pattern + "%", '\\');
    }

    private static String wholeTermPattern(String brand) {
        StringBuilder pattern = new StringBuilder("(^|[^[:alnum:]])");
        for (char letter : brand.toCharArray()) {
            if ("\\.^$|?*+()[]{}".indexOf(letter) >= 0) pattern.append('\\');
            pattern.append(letter);
        }
        return pattern.append("($|[^[:alnum:]])").toString();
    }

    private static Expression<Boolean> matchesBrand(CriteriaBuilder cb, Expression<String> field, String pattern) {
        return cb.function("regexp_like", Boolean.class, field, cb.literal(pattern), cb.literal("i"));
    }

    private static List<String> conditionValues(String condition) {
        return switch (condition) {
            case "NEW" -> List.of("NEW", "BRAND NEW", "MỚI");
            case "LIKE_NEW" -> List.of("LIKE_NEW", "LIKE NEW", "LIKE-NEW", "NHƯ MỚI");
            case "USED_GOOD" -> List.of("USED_GOOD", "GOOD", "TỐT");
            case "USED_FAIR" -> List.of("USED_FAIR", "FAIR", "KHÁ");
            default -> List.of(condition);
        };
    }

    private static Specification<Post> hasPublishedInspection() {
        // Keep this qualification consistent with findLatestListingInspections.
        return (post, query, cb) -> {
            var inspections = query.subquery(Integer.class);
            var inspection = inspections.from(InspectionOrder.class);
            var settlements = inspections.subquery(Integer.class);
            var settlement = settlements.from(InspectionFeeSettlement.class);
            settlements.select(cb.literal(1)).where(
                    cb.equal(settlement.get("inspectionOrder"), inspection),
                    cb.equal(settlement.get("outcome"), InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED),
                    cb.isNotNull(settlement.get("returnShippingSettledAt")));
            inspections.select(cb.literal(1)).where(
                    cb.equal(inspection.get("post"), post),
                    cb.or(cb.isNotNull(inspection.get("tamperSealId")),
                            cb.equal(inspection.get("status"), "PASSED"),
                            cb.and(cb.equal(inspection.get("status"), "CANCELLED"), cb.exists(settlements))));
            return cb.exists(inspections);
        };
    }

    @Override
    public MarketplaceListingResponse get(UUID postId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new NotFoundException("Listing not found"));
        if (!"ACTIVE".equals(post.getStatus()) && !"RESERVED".equals(post.getStatus())) {
            throw new NotFoundException("Listing is not available for purchase");
        }
        var matches = inspections.findLatestListingInspections(List.of(postId));
        return toResponse(post, matches.isEmpty() ? null : matches.getFirst());
    }

    private MarketplaceListingResponse toResponse(Post post, ListingInspection inspection) {
        if (inspection == null) {
            return postMapper.toMarketplaceResponse(post, false, null, null);
        }
        return postMapper.toMarketplaceResponse(post, true, inspection.getTamperSealId(), inspection.getConditionGrade());
    }
}

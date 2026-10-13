package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.shipping.ShippingQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InspectionPricingService {

    private final PostRepository postRepository;
    private final InspectionTravelFeeCalculator travelFeeCalculator;
    private final InspectionFeeProposalRepository proposalRepository;
    private final ShippingQuoteService shippingQuoteService;

    @Value("${app.inspection.inspection-fee:100000}")
    private BigDecimal defaultBaseInspectionFee;

    @Value("${app.inspection.shipping-fee:50000}")
    private BigDecimal defaultShippingFee;

    @Transactional(readOnly = true)
    public InspectionQuoteResponse quote(InspectionQuoteRequest request) {
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new NotFoundException("Post not found"));

        BigDecimal baseFee = resolveBaseInspectionFee(post);

        // Lấy tọa độ Seller từ pickup address
        ShippingAddress sellerPickup = null;
        try {
            sellerPickup = shippingQuoteService.pickup(post.getUser().getId());
        } catch (Exception ignored) {}

        BigDecimal sellerLat = sellerPickup != null ? sellerPickup.latitude() : BigDecimal.valueOf(10.7769);
        BigDecimal sellerLon = sellerPickup != null ? sellerPickup.longitude() : BigDecimal.valueOf(106.7009);

        // 1. Tính toán gói AT_HOME
        InspectionTravelFeeCalculator.TravelFeeResult travelResult =
                travelFeeCalculator.calculate(sellerLat, sellerLon, null, null);

        BigDecimal atHomeTotal = baseFee.add(travelResult.getRoundTripFee());
        Map<String, BigDecimal> atHomeSplits = calculateSplits(atHomeTotal);

        // 2. Tính toán gói AT_CENTER
        BigDecimal shippingToCenterFee = defaultShippingFee;
        BigDecimal returnReserveFee = defaultShippingFee;
        BigDecimal atCenterTotal = baseFee.add(shippingToCenterFee).add(returnReserveFee);
        Map<String, BigDecimal> atCenterSplits = calculateSplits(atCenterTotal);

        return InspectionQuoteResponse.builder()
                .postId(post.getId())
                .postTitle(post.getTitle())
                .postPrice(post.getPrice())
                .baseInspectionFee(baseFee)
                .travelDistanceKm(travelResult.getDistanceKm())
                .travelFeeOneWay(travelResult.getOneWayFee())
                .travelFeeRoundTrip(travelResult.getRoundTripFee())
                .atHomeTotalFee(atHomeTotal)
                .appliedTiers(travelResult.getAppliedTiers())
                .atHomeSplitOptions(atHomeSplits)
                .shippingToCenterFee(shippingToCenterFee)
                .returnShippingReserveFee(returnReserveFee)
                .atCenterTotalFee(atCenterTotal)
                .atCenterSplitOptions(atCenterSplits)
                .build();
    }

    public BigDecimal resolveBaseInspectionFee(Post post) {
        if (post != null && post.getCategoryId() != null) {
            UUID categoryId = post.getCategoryId();
            Optional<InspectionFeeProposal> proposal =
                    proposalRepository.findFirstByCategoryIdAndStatusOrderByReviewedAtDesc(categoryId, InspectionFeeProposalStatus.APPROVED);
            if (proposal.isPresent()) {
                return proposal.get().getBaseInspectionFee();
            }
        }
        return defaultBaseInspectionFee;
    }

    private Map<String, BigDecimal> calculateSplits(BigDecimal total) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("BUYER_PAYS", total);
        map.put("SELLER_PAYS", total);
        map.put("SPLIT_50_50", total.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP));
        return map;
    }
}

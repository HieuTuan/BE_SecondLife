package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionFeeProposalRequest;
import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.InspectionTravelTier;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.InspectionTravelTierRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionTravelFeeCalculator;
import com.secondlife.secondlife.service.shipping.ShippingQuoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionPricingServiceImpl implements InspectionPricingService {

    private final PostRepository postRepository;
    private final InspectionTravelFeeCalculator travelFeeCalculator;
    private final InspectionFeeProposalRepository proposalRepository;
    private final InspectionTravelTierRepository travelTierRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ShippingQuoteService shippingQuoteService;
    private final ObjectMapper objectMapper;

    @Value("${app.inspection.inspection-fee:100000}")
    private BigDecimal defaultBaseInspectionFee;

    @Value("${app.inspection.shipping-fee:50000}")
    private BigDecimal defaultShippingFee;

    @Override
    @Transactional(readOnly = true)
    public InspectionQuoteResponse calculateQuote(InspectionQuoteRequest request) {
        return quote(request);
    }

    @Override
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

        BigDecimal atHomeSplit50 = atHomeTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal atCenterSplit50 = atCenterTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        InspectionQuoteResponse.AtHomePackageDto atHomePkg = InspectionQuoteResponse.AtHomePackageDto.builder()
                .distanceKm(travelResult.getDistanceKm())
                .travelFeeOneWay(travelResult.getOneWayFee())
                .travelFeeRoundTrip(travelResult.getRoundTripFee())
                .totalFee(atHomeTotal)
                .suggestedSplit50(atHomeSplit50)
                .tiersBreakdown(travelResult.getAppliedTiers())
                .build();

        InspectionQuoteResponse.AtCenterPackageDto atCenterPkg = InspectionQuoteResponse.AtCenterPackageDto.builder()
                .shippingToCenterFee(shippingToCenterFee)
                .returnReserveFee(returnReserveFee)
                .totalFee(atCenterTotal)
                .suggestedSplit50(atCenterSplit50)
                .build();

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
                .atHomePackage(atHomePkg)
                .atCenterPackage(atCenterPkg)
                .build();
    }

    @Override
    public BigDecimal resolveBaseInspectionFee(Post post) {
        if (post != null && post.getCategoryId() != null) {
            UUID categoryId = post.getCategoryId();
            Optional<InspectionFeeProposal> proposal =
                    proposalRepository.findFirstByCategory_IdAndStatusOrderByReviewedAtDesc(categoryId, InspectionFeeProposalStatus.APPROVED);
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

    @Override
    @Transactional(readOnly = true)
    public List<InspectionTravelTierDto> getActiveTiers() {
        return travelTierRepository.findAllActive().stream()
                .map(t -> InspectionTravelTierDto.builder()
                        .tierNumber(t.getTierNumber())
                        .name(t.getName())
                        .fromKm(t.getFromKm())
                        .toKm(t.getToKm())
                        .pricePerKm(t.getPricePerKm())
                        .status(t.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<InspectionTravelTierDto> updateTiers(List<InspectionTravelTierDto> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) return getActiveTiers();

        for (InspectionTravelTierDto dto : dtoList) {
            InspectionTravelTier tier = travelTierRepository.findByTierNumber(dto.getTierNumber())
                    .orElseGet(() -> {
                        InspectionTravelTier newTier = new InspectionTravelTier();
                        newTier.setTierNumber(dto.getTierNumber());
                        return newTier;
                    });
            tier.setName(dto.getName());
            tier.setFromKm(dto.getFromKm());
            tier.setToKm(dto.getToKm());
            tier.setPricePerKm(dto.getPricePerKm());
            tier.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
            travelTierRepository.save(tier);
        }

        return getActiveTiers();
    }

    @Override
    @Transactional
    public InspectionFeeProposalResponse proposeFee(UUID proposedById, InspectionFeeProposalRequest request) {
        User proposer = userRepository.findById(proposedById)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String tiersJson;
        try {
            tiersJson = objectMapper.writeValueAsString(request.getTiers());
        } catch (Exception e) {
            tiersJson = "[]";
        }

        InspectionFeeProposal proposal = new InspectionFeeProposal();
        if (request.getCategoryId() != null) {
            categoryRepository.findById(request.getCategoryId())
                    .ifPresent(proposal::setCategory);
        }
        proposal.setBaseInspectionFee(request.getBaseInspectionFee());
        proposal.setTiersJson(tiersJson);
        proposal.setProposedBy(proposer);
        proposal.setStatus(InspectionFeeProposalStatus.PENDING_APPROVAL);

        InspectionFeeProposal saved = proposalRepository.save(proposal);
        return toProposalResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionFeeProposalResponse> getProposals(InspectionFeeProposalStatus status, Pageable pageable) {
        if (status != null) {
            return proposalRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                    .map(this::toProposalResponse);
        }
        return proposalRepository.findAll(pageable).map(this::toProposalResponse);
    }

    @Override
    @Transactional
    public InspectionFeeProposalResponse reviewProposal(UUID proposalId, UUID reviewerId, boolean approved, String reason) {
        InspectionFeeProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NotFoundException("Fee proposal not found"));

        if (proposal.getStatus() != InspectionFeeProposalStatus.PENDING_APPROVAL) {
            throw new ConflictException("Proposal has already been reviewed");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new NotFoundException("Reviewer not found"));

        proposal.setReviewedBy(reviewer);
        proposal.setReviewedAt(Instant.now());
        proposal.setStatus(approved ? InspectionFeeProposalStatus.APPROVED : InspectionFeeProposalStatus.REJECTED);
        proposal.setRejectReason(approved ? null : reason);

        if (approved) {
            try {
                List<InspectionTravelTierDto> tiers = objectMapper.readValue(proposal.getTiersJson(), new TypeReference<List<InspectionTravelTierDto>>() {});
                if (tiers != null && !tiers.isEmpty()) {
                    updateTiers(tiers);
                }
            } catch (Exception ex) {
                log.warn("Failed to apply tier changes from approved proposal: {}", ex.getMessage());
            }
        }

        return toProposalResponse(proposalRepository.save(proposal));
    }

    private InspectionFeeProposalResponse toProposalResponse(InspectionFeeProposal p) {
        List<InspectionTravelTierDto> tiers = Collections.emptyList();
        try {
            if (p.getTiersJson() != null && !p.getTiersJson().isBlank()) {
                tiers = objectMapper.readValue(p.getTiersJson(), new TypeReference<List<InspectionTravelTierDto>>() {});
            }
        } catch (Exception ignored) {}

        return InspectionFeeProposalResponse.builder()
                .id(p.getId())
                .categoryId(p.getCategoryId())
                .baseInspectionFee(p.getBaseInspectionFee())
                .tiers(tiers)
                .proposedById(p.getProposedBy() != null ? p.getProposedBy().getId() : null)
                .proposedByName(p.getProposedBy() != null ? p.getProposedBy().getEmail() : null)
                .reviewedById(p.getReviewedBy() != null ? p.getReviewedBy().getId() : null)
                .reviewedByName(p.getReviewedBy() != null ? p.getReviewedBy().getEmail() : null)
                .status(p.getStatus())
                .rejectReason(p.getRejectReason())
                .createdAt(p.getCreatedAt())
                .reviewedAt(p.getReviewedAt())
                .build();
    }
}

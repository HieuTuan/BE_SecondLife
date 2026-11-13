package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.config.LalamoveProperties;
import com.secondlife.secondlife.dto.request.InspectionFeeProposalRequest;
import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionCenterOptionResponse;
import com.secondlife.secondlife.dto.response.InspectionConfigurationResponse;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.InspectionTravelTier;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.InspectionPricingMapper;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.InspectionTravelTierRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionConfigurationService;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import com.secondlife.secondlife.service.InspectionShippingQuoteService;
import com.secondlife.secondlife.dto.response.InspectionShippingQuoteResponse;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.service.InspectionGovernanceAccessService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.ShippingQuoteService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@Service
public class InspectionPricingServiceImpl implements InspectionPricingService {

    private final PostRepository postRepository;
    private final InspectionTravelFeeCalculator travelFeeCalculator;
    private final InspectionFeeProposalRepository proposalRepository;
    private final InspectionTravelTierRepository travelTierRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ShippingQuoteService shippingQuoteService;
    private final ObjectMapper objectMapper;
    private final InspectionPricingMapper inspectionPricingMapper;
    private final InspectionConfigurationService configurations;
    private final InspectionGovernanceAccessService access;
    private final InspectionCenterProfileService centerProfiles;
    private final InspectionShippingQuoteService inspectionShipping;
    private final boolean cashShippingSimulationEnabled;

    public InspectionPricingServiceImpl(PostRepository postRepository, InspectionTravelFeeCalculator travelFeeCalculator, InspectionFeeProposalRepository proposalRepository, InspectionTravelTierRepository travelTierRepository, UserRepository userRepository, CategoryRepository categoryRepository, ShippingQuoteService shippingQuoteService, ObjectMapper objectMapper, InspectionPricingMapper inspectionPricingMapper, InspectionConfigurationService configurations, InspectionGovernanceAccessService access, InspectionCenterProfileService centerProfiles, InspectionShippingQuoteService inspectionShipping, LalamoveProperties lalamove, @Value("${app.inspection.cash-shipping-simulation-enabled:false}") boolean cashShippingSimulationEnabled) {
        this.postRepository = postRepository;
        this.travelFeeCalculator = travelFeeCalculator;
        this.proposalRepository = proposalRepository;
        this.travelTierRepository = travelTierRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.shippingQuoteService = shippingQuoteService;
        this.objectMapper = objectMapper;
        this.inspectionPricingMapper = inspectionPricingMapper;
        this.configurations = configurations;
        this.access = access;
        this.centerProfiles = centerProfiles;
        this.inspectionShipping = inspectionShipping;
        if (cashShippingSimulationEnabled && !lalamove.isSandbox()) {
            throw new IllegalStateException("Inspection cash shipping simulation requires Lalamove Sandbox");
        }
        this.cashShippingSimulationEnabled = cashShippingSimulationEnabled;
    }

    @Override
    @Transactional
    public InspectionQuoteResponse calculateQuote(InspectionQuoteRequest request) {
        return quote(request);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionCenterOptionResponse> getAvailableCenters() {
        return centerProfiles.getAvailableCenters();
    }

    @Override
    @Transactional
    public InspectionQuoteResponse quote(InspectionQuoteRequest request) {
        Post post = postRepository.findById(request.postId()).orElseThrow(() -> new NotFoundException("Post not found"));

        InspectionConfigurationResponse configuration = configurations.resolve(post, request.inspectionCenterId());
        if ((request.expectedFeeProposalId() != null && !request.expectedFeeProposalId().equals(configuration.feeProposalId())) || (request.expectedChecklistTemplateId() != null && !request.expectedChecklistTemplateId().equals(configuration.checklistTemplateId()))) {
            throw new ConflictException("Biểu phí hoặc checklist đã thay đổi; vui lòng lấy báo giá mới trước khi xác nhận");
        }
        BigDecimal baseFee = configuration.inspectionFee();

        // Lấy tọa độ Seller từ pickup address
        ShippingAddress sellerPickup = shippingQuoteService.pickup(post.getUser().getId());
        if (sellerPickup == null || sellerPickup.latitude() == null || sellerPickup.longitude() == null || sellerPickup.latitude().abs().compareTo(BigDecimal.valueOf(90)) > 0 || sellerPickup.longitude().abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new ConflictException("Người bán cần cấu hình địa chỉ lấy hàng và GPS trước khi báo phí kiểm định");
        }
        User center = userRepository.findById(configuration.inspectionCenterId()).orElseThrow(() -> new NotFoundException("Inspection center not found"));
        if (center.getProfile() == null || center.getProfile().getLatitude() == null || center.getProfile().getLongitude() == null || !Double.isFinite(center.getProfile().getLatitude()) || !Double.isFinite(center.getProfile().getLongitude()) || Math.abs(center.getProfile().getLatitude()) > 90 || Math.abs(center.getProfile().getLongitude()) > 180) {
            throw new ConflictException("Trung tâm cần cập nhật địa chỉ và GPS trong hồ sơ trước khi báo phí kiểm định");
        }
        BigDecimal sellerLat = sellerPickup.latitude();
        BigDecimal sellerLon = sellerPickup.longitude();
        BigDecimal centerLat = BigDecimal.valueOf(center.getProfile().getLatitude());
        BigDecimal centerLon = BigDecimal.valueOf(center.getProfile().getLongitude());

        // 1. Tính toán gói AT_HOME
        InspectionTravelFeeCalculator.TravelFeeResult travelResult = travelFeeCalculator.calculate(sellerLat, sellerLon, centerLat, centerLon);

        BigDecimal atHomeTotal = baseFee.add(travelResult.getRoundTripFee());
        Map<String, BigDecimal> atHomeSplits = calculateSplits(atHomeTotal);

        // 2. Tính toán gói AT_CENTER
        InspectionShippingQuoteResponse shippingQuote = request.orderType() == InspectionOrderType.AT_HOME ? null : inspectionShipping.quote(post.getId(), configuration.inspectionCenterId(), request.inspectionShippingQuoteId());
        BigDecimal shippingToCenterFee = shippingQuote == null ? null : shippingQuote.outbound().shippingFee();
        BigDecimal returnReserveFee = shippingQuote == null ? null : shippingQuote.returnReserve().shippingFee();
        BigDecimal atCenterTotal = shippingQuote == null ? null : baseFee.add(shippingToCenterFee).add(returnReserveFee);
        Map<String, BigDecimal> atCenterSplits = shippingQuote == null ? null : calculateSplits(atCenterTotal);

        BigDecimal atHomeSplit50 = atHomeTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal atCenterSplit50 = shippingQuote == null ? null : atCenterTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

        InspectionQuoteResponse.AtHomePackageDto atHomePkg = InspectionQuoteResponse.AtHomePackageDto.builder().distanceKm(travelResult.getDistanceKm()).travelFeeOneWay(travelResult.getOneWayFee()).travelFeeRoundTrip(travelResult.getRoundTripFee()).totalFee(atHomeTotal).suggestedSplit50(atHomeSplit50).tiersBreakdown(travelResult.getAppliedTiers()).build();

        InspectionQuoteResponse.AtCenterPackageDto atCenterPkg = shippingQuote == null ? null : InspectionQuoteResponse.AtCenterPackageDto.builder().shippingToCenterFee(shippingToCenterFee).returnReserveFee(returnReserveFee).totalFee(atCenterTotal).suggestedSplit50(atCenterSplit50).build();

        return InspectionQuoteResponse.builder().postId(post.getId()).postTitle(post.getTitle()).postPrice(post.getPrice()).baseInspectionFee(baseFee).travelDistanceKm(travelResult.getDistanceKm()).travelFeeOneWay(travelResult.getOneWayFee()).travelFeeRoundTrip(travelResult.getRoundTripFee()).atHomeTotalFee(atHomeTotal).appliedTiers(travelResult.getAppliedTiers()).atHomeSplitOptions(atHomeSplits).shippingToCenterFee(shippingToCenterFee).returnShippingReserveFee(returnReserveFee).atCenterTotalFee(atCenterTotal).atCenterSplitOptions(atCenterSplits).atHomePackage(atHomePkg).atCenterPackage(atCenterPkg).inspectionConfiguration(configuration).shippingQuote(shippingQuote).cashShippingSimulationEnabled(shippingQuote != null && cashShippingSimulationEnabled).build();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal resolveBaseInspectionFee(Post post) {
        return configurations.resolve(post, null).inspectionFee();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyConfiguration(InspectionOrder order, InspectionQuoteResponse quote) {
        configurations.snapshot(order, quote.inspectionConfiguration());
        inspectionShipping.bind(order, quote.shippingQuote());
    }

    private Map<String, BigDecimal> calculateSplits(BigDecimal total) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("BUYER_PAYS", total);
        map.put("SELLER_PAYS", total);
        map.put("SPLIT_50_50", total.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP));
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionTravelTierDto> getActiveTiers() {
        return travelTierRepository.findAllActive().stream().map(inspectionPricingMapper::toTierResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<InspectionTravelTierDto> updateTiers(List<InspectionTravelTierDto> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) return getActiveTiers();

        for (InspectionTravelTierDto dto : dtoList) {
            InspectionTravelTier tier = travelTierRepository.findByTierNumber(dto.tierNumber()).orElseGet(() -> {
                InspectionTravelTier newTier = new InspectionTravelTier();
                newTier.setTierNumber(dto.tierNumber());
                return newTier;
            });
            tier.setName(dto.name());
            tier.setFromKm(dto.fromKm());
            tier.setToKm(dto.toKm());
            tier.setPricePerKm(dto.pricePerKm());
            tier.setStatus(dto.status() != null ? dto.status() : "ACTIVE");
            travelTierRepository.save(tier);
        }

        return getActiveTiers();
    }

    @Override
    @Transactional
    public InspectionFeeProposalResponse proposeFee(UUID proposedById, InspectionFeeProposalRequest request) {
        User proposer = access.requireCenter(proposedById);
        if (request.baseInspectionFee() == null || request.baseInspectionFee().signum() < 0 || request.baseInspectionFee().scale() > 2 || request.baseInspectionFee().precision() - request.baseInspectionFee().scale() > 16) {
            throw new BadRequestException("Phí kiểm định phải không âm, tối đa 16 chữ số nguyên và 2 chữ số thập phân");
        }
        if (request.complexityLevel() != null && (request.categoryId() != null || (request.tiers() != null && !request.tiers().isEmpty()))) {
            throw new BadRequestException("Giá L1-L4 áp dụng theo trung tâm; không gửi kèm categoryId hoặc bậc phí di chuyển");
        }
        if (request.complexityLevel() == null && request.categoryId() == null && (request.tiers() == null || request.tiers().isEmpty())) {
            throw new BadRequestException("Cần chọn cấp L1-L4 để đề xuất phí kiểm định");
        }

        String tiersJson;
        try {
            tiersJson = objectMapper.writeValueAsString(request.tiers() == null ? List.of() : request.tiers());
        } catch (RuntimeException e) {
            throw new BadRequestException("Không đọc được các bậc phí di chuyển");
        }

        InspectionFeeProposal proposal = new InspectionFeeProposal();
        if (request.categoryId() != null) {
            proposal.setCategory(categoryRepository.findById(request.categoryId()).orElseThrow(() -> new NotFoundException("Category not found")));
        }
        proposal.setComplexityLevel(request.complexityLevel());
        proposal.setBaseInspectionFee(request.baseInspectionFee());
        proposal.setTiersJson(tiersJson);
        proposal.setProposedBy(proposer);
        proposal.setStatus(InspectionFeeProposalStatus.PENDING_APPROVAL);

        InspectionFeeProposal saved = proposalRepository.save(proposal);
        return toProposalResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionFeeProposalResponse> getProposals(InspectionFeeProposalStatus status, Pageable pageable) {
        pageable = InspectionPageables.validate(pageable, Set.of("id", "createdAt", "reviewedAt", "status", "complexityLevel"));
        if (status != null) {
            return proposalRepository.findByStatusOrderByCreatedAtDesc(status, pageable).map(this::toProposalResponse);
        }
        return proposalRepository.findAll(pageable).map(this::toProposalResponse);
    }

    @Override
    @Transactional
    public InspectionFeeProposalResponse reviewProposal(UUID proposalId, UUID reviewerId, boolean approved, String reason) {
        User reviewer = access.requireAdmin(reviewerId);
        InspectionFeeProposal proposal = proposalRepository.findByIdForUpdate(proposalId).orElseThrow(() -> new NotFoundException("Fee proposal not found"));

        if (proposal.getStatus() != InspectionFeeProposalStatus.PENDING_APPROVAL) {
            throw new ConflictException("Proposal has already been reviewed");
        }

        if (reviewerId.equals(proposal.getProposedBy().getId())) {
            throw new ForbiddenException("Người gửi không được tự duyệt đề xuất");
        }
        if (!approved && (reason == null || reason.isBlank())) throw new BadRequestException("Cần nêu lý do từ chối");
        if (reason != null && reason.length() > 2000) throw new BadRequestException("Lý do tối đa 2000 ký tự");

        proposal.setReviewedBy(reviewer);
        proposal.setReviewedAt(Instant.now());
        proposal.setStatus(approved ? InspectionFeeProposalStatus.APPROVED : InspectionFeeProposalStatus.REJECTED);
        proposal.setRejectReason(approved ? null : reason);

        if (approved && proposal.getComplexityLevel() == null) {
            List<InspectionTravelTierDto> tiers;
            try {
                tiers = objectMapper.readValue(proposal.getTiersJson(), new TypeReference<List<InspectionTravelTierDto>>() {
                });
            } catch (RuntimeException ex) {
                throw new BadRequestException("Đề xuất có dữ liệu bậc phí không hợp lệ; không thể duyệt");
            }
            if (tiers != null && !tiers.isEmpty()) updateTiers(tiers);
        }

        return toProposalResponse(proposalRepository.save(proposal));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionFeeProposalResponse> getCenterProposals(UUID centerId, InspectionFeeProposalStatus status, Pageable pageable) {
        access.requireCenter(centerId);
        pageable = InspectionPageables.validate(pageable, Set.of("id", "createdAt", "reviewedAt", "status", "complexityLevel"));
        return proposalRepository.findByAuthor(centerId, status, pageable).map(this::toProposalResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionFeeProposalResponse> getCenterLevelFees(UUID centerId) {
        access.requireCenter(centerId);
        return proposalRepository.findCurrentLevelFees(centerId).stream().map(this::toProposalResponse).toList();
    }

    private InspectionFeeProposalResponse toProposalResponse(InspectionFeeProposal p) {
        List<InspectionTravelTierDto> tiers = Collections.emptyList();
        try {
            if (p.getTiersJson() != null && !p.getTiersJson().isBlank()) {
                tiers = objectMapper.readValue(p.getTiersJson(), new TypeReference<List<InspectionTravelTierDto>>() {
                });
            }
        } catch (RuntimeException exception) {
            throw new ConflictException("Đề xuất biểu phí có dữ liệu bậc di chuyển không hợp lệ; cần kiểm tra lại cấu hình");
        }

        return inspectionPricingMapper.toProposalResponse(p, tiers);
    }
}

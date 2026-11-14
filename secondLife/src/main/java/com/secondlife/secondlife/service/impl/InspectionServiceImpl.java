package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ChecklistItemResultDto;
import com.secondlife.secondlife.dto.request.ConfirmInspectionPurchaseRequest;
import com.secondlife.secondlife.dto.request.InspectionAppealRequest;
import com.secondlife.secondlife.dto.request.InspectionAppointmentCounterRequest;
import com.secondlife.secondlife.dto.request.InspectionAppointmentRejectRequest;
import com.secondlife.secondlife.dto.request.InspectionAppointmentRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyerRespondRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.request.InspectionRejectPurchaseRequest;
import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.request.InspectionSealScanRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.request.InspectionStartRequest;
import com.secondlife.secondlife.dto.request.ReviewInspectionAppealRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.dto.request.ShippingQuoteRequest;
import com.secondlife.secondlife.dto.response.InspectionCheckInCodeResponse;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.dto.response.InspectionSealLogResponse;
import com.secondlife.secondlife.dto.response.InspectionSealVerificationResponse;
import com.secondlife.secondlife.dto.response.ShippingQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.InspectionSealLog;
import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.ShippingQuote;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.entity.UserRole;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.InspectionAppealStatus;
import com.secondlife.secondlife.enums.InspectionAppointmentShift;
import com.secondlife.secondlife.enums.InspectionAppointmentStatus;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.OrderPaymentMethod;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.exception.AiProviderException;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.InspectionCenterProfileMapper;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.mapper.InspectionSealLogMapper;
import com.secondlife.secondlife.mapper.ShippingPayloads;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.InspectionSealLogRepository;
import com.secondlife.secondlife.repository.InspectionShippingQuoteRepository;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.CommissionService;
import com.secondlife.secondlife.service.InspectionFeeSettlementService;
import com.secondlife.secondlife.service.InspectionOrderChecklistService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;
import com.secondlife.secondlife.service.ListingPublicationService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.PaidOrderService;
import com.secondlife.secondlife.service.SellerReputationService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import com.secondlife.secondlife.service.TamperSealService;
import com.secondlife.secondlife.service.WalletService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class InspectionServiceImpl implements InspectionService {

    private static final ZoneId INSPECTION_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> APPOINTMENT_ORDER_STATES = Set.of(
            InspectionOrderStatus.CONFIRMED_PENDING_DISPATCH.name(),
            InspectionOrderStatus.DISPATCHED.name(),
            InspectionOrderStatus.SCHEDULED.name());

    private static final Duration INSPECTION_FEE_PAYMENT_WINDOW = Duration.ofHours(12);

    private final InspectionOrderRepository inspectionOrderRepository;
    private final PostRepository postRepository;
    private final OrderRepository orderRepository;
    private final NegotiationRepository negotiationRepository;
    private final ShipmentRepository shipments;
    private final InspectionShippingQuoteRepository inspectionShippingQuotes;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final ListingPublicationService publication;
    private final InspectionPricingService inspectionPricingService;
    private final InspectionOrderChecklistService orderChecklists;
    private final WalletService walletService;
    private final CommissionService commissions;
    private final ShippingQuoteService shippingQuoteService;
    private final PaidOrderService paidOrderService;
    private final InspectionFeeSettlementService feeSettlementService;
    private final SellerReputationService sellerReputationService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final InspectionSealLogRepository inspectionSealLogRepository;
    private final TamperSealService tamperSealService;

    @Qualifier("googleChatGateway")
    private final AiChatGateway googleChatGateway;
    private final InspectionCenterProfileMapper inspectionCenterProfileMapper;
    private final InspectionOrderMapper inspectionOrderMapper;
    private final InspectionSealLogMapper inspectionSealLogMapper;

    private final int approvalTtlHours;
    private final Pattern evidencePhotoUrl;
    private final BigDecimal buyerCommitmentDepositRate;
    private final BigDecimal buyerCommitmentDepositMax;

    public InspectionServiceImpl(
            InspectionOrderRepository inspectionOrderRepository,
            PostRepository postRepository,
            OrderRepository orderRepository,
            NegotiationRepository negotiationRepository,
            ShipmentRepository shipments,
            InspectionShippingQuoteRepository inspectionShippingQuotes,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            ListingPublicationService publication,
            InspectionPricingService inspectionPricingService,
            WalletService walletService,
            CommissionService commissions,
            ShippingQuoteService shippingQuoteService,
            PaidOrderService paidOrderService,
            InspectionFeeSettlementService feeSettlementService,
            SellerReputationService sellerReputationService,
            ObjectMapper objectMapper,
            NotificationService notificationService,
            InspectionSealLogRepository inspectionSealLogRepository,
            TamperSealService tamperSealService,
            @Qualifier("googleChatGateway") AiChatGateway googleChatGateway,
            InspectionCenterProfileMapper inspectionCenterProfileMapper,
            InspectionOrderMapper inspectionOrderMapper,
            InspectionSealLogMapper inspectionSealLogMapper,
            @Value("${app.inspection.approval-ttl-hours:24}") int approvalTtlHours,
            @Value("${app.inspection.buyer-commitment-deposit-rate:0.00}") BigDecimal buyerCommitmentDepositRate,
            @Value("${app.inspection.buyer-commitment-deposit-max:500000}") BigDecimal buyerCommitmentDepositMax,
            InspectionOrderChecklistService orderChecklists,
            @Value("${app.cloudinary.cloud-name}") String cloudName) {
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.postRepository = postRepository;
        this.orderRepository = orderRepository;
        this.negotiationRepository = negotiationRepository;
        this.shipments = shipments;
        this.inspectionShippingQuotes = inspectionShippingQuotes;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.publication = publication;
        this.inspectionPricingService = inspectionPricingService;
        this.orderChecklists = orderChecklists;
        this.evidencePhotoUrl = Pattern.compile("https://res\\.cloudinary\\.com/" + Pattern.quote(cloudName)
                + "/image/upload/(?:v[0-9]+/)?secondlife/inspections/proofs/[0-9a-fA-F-]{36}\\.(?:jpg|jpeg|png|webp)");
        this.walletService = walletService;
        this.commissions = commissions;
        this.shippingQuoteService = shippingQuoteService;
        this.paidOrderService = paidOrderService;
        this.feeSettlementService = feeSettlementService;
        this.sellerReputationService = sellerReputationService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.inspectionSealLogRepository = inspectionSealLogRepository;
        this.tamperSealService = tamperSealService;
        this.googleChatGateway = googleChatGateway;
        this.inspectionCenterProfileMapper = inspectionCenterProfileMapper;
        this.inspectionOrderMapper = inspectionOrderMapper;
        this.inspectionSealLogMapper = inspectionSealLogMapper;
        this.approvalTtlHours = approvalTtlHours > 0 ? approvalTtlHours : 24;
        this.buyerCommitmentDepositRate = buyerCommitmentDepositRate != null ? buyerCommitmentDepositRate
                : BigDecimal.ZERO;
        this.buyerCommitmentDepositMax = buyerCommitmentDepositMax != null ? buyerCommitmentDepositMax
                : new BigDecimal("500000");
    }

    @Override
    @Transactional
    public InspectionOrderResponse requestInspection(UUID buyerId, InspectionBuyRequest request) {
        if (request.orderType() == InspectionOrderType.AT_CENTER && request.inspectionShippingQuoteId() == null) {
            throw new BadRequestException("inspectionShippingQuoteId là bắt buộc; lấy báo giá Lalamove kiểm định trước khi tạo yêu cầu AT_CENTER");
        }
        Post post = postRepository.findByIdForUpdate(request.postId())
                .orElseThrow(() -> new NotFoundException("Bài đăng không tồn tại"));

        if (post.getUser() != null && post.getUser().getId().equals(buyerId)) {
            throw new BadRequestException("Bạn không thể tạo yêu cầu kiểm định cho bài đăng của chính mình");
        }

        if (!"ACTIVE".equals(post.getStatus()) && !"RESERVED".equals(post.getStatus())) {
            throw new BadRequestException("Bài đăng hiện không ở trạng thái sẵn sàng để giao dịch");
        }

        if ("RESERVED".equals(post.getStatus()) && request.negotiationId() == null) {
            throw new ConflictException("Bài đăng đang được giữ chỗ. Bạn cần có thương lượng giá hợp lệ để tiếp tục.");
        }

        // Kiểm tra xem bài đăng đã có đơn kiểm định đang xử lý không
        var activeInspection = inspectionOrderRepository.findActiveByPostId(post.getId());
        if (activeInspection.isPresent()) {
            throw new ConflictException("Bài đăng đã có đơn kiểm định khác đang diễn ra");
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin người mua"));
        User seller = post.getUser();

        Negotiation negotiation = null;
        BigDecimal finalItemPrice = post.getPrice();
        if (request.negotiationId() != null) {
            negotiation = negotiationRepository.findById(request.negotiationId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy thương lượng giá"));
            if (!negotiation.getBuyer().getId().equals(buyerId)
                    || !negotiation.getPost().getId().equals(post.getId())) {
                throw new BadRequestException("Thương lượng giá không thuộc về bạn hoặc bài đăng này");
            }
            if (negotiation.getStatus() != NegotiationStatus.ACCEPTED) {
                throw new BadRequestException("Thương lượng giá chưa được người bán chấp thuận");
            }
            if (negotiation.getExpiredAt() != null && negotiation.getExpiredAt().isBefore(Instant.now())) {
                throw new BadRequestException("Thương lượng giá đã hết hạn");
            }
            finalItemPrice = negotiation.getOfferedPrice();
            negotiation.setExpiredAt(Instant.now().plus(Duration.ofHours(72)));
            negotiationRepository.save(negotiation);
        }

        // Tính toán báo giá chi tiết cho 2 gói
        InspectionQuoteResponse quote = inspectionPricingService.calculateQuote(
                InspectionQuoteRequest.builder()
                        .postId(post.getId())
                        .buyerLatitude(request.buyerLatitude())
                        .buyerLongitude(request.buyerLongitude())
                        .inspectionCenterId(request.inspectionCenterId())
                        .expectedFeeProposalId(request.expectedFeeProposalId())
                        .expectedChecklistTemplateId(request.expectedChecklistTemplateId())
                        .orderType(request.orderType())
                        .inspectionShippingQuoteId(request.inspectionShippingQuoteId())
                        .build());

        InspectionOrderType orderType = request.orderType() != null ? request.orderType()
                : InspectionOrderType.AT_CENTER;
        InspectionFeePayer feePayer = request.feePayer() != null ? request.feePayer() : InspectionFeePayer.BUYER_PAYS;

        BigDecimal inspectionFee = quote.baseInspectionFee();
        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal travelDist = BigDecimal.ZERO;
        BigDecimal travelFee = BigDecimal.ZERO;
        BigDecimal returnReserve = BigDecimal.ZERO;
        BigDecimal totalFee;

        if (orderType == InspectionOrderType.AT_HOME) {
            travelDist = quote.travelDistanceKm();
            travelFee = quote.travelFeeRoundTrip();
            totalFee = quote.atHomeTotalFee();
        } else {
            shippingFee = quote.shippingToCenterFee();
            returnReserve = quote.returnShippingReserveFee();
            totalFee = quote.atCenterTotalFee();
        }

        BigDecimal commitmentDeposit = BigDecimal.ZERO;
        if (buyerCommitmentDepositRate.compareTo(BigDecimal.ZERO) > 0) {
            commitmentDeposit = finalItemPrice.multiply(buyerCommitmentDepositRate)
                    .setScale(2, RoundingMode.HALF_UP)
                    .min(buyerCommitmentDepositMax);
        }

        InspectionOrder order = new InspectionOrder();
        order.setPost(post);
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setNegotiation(negotiation);
        order.setFinalItemPrice(finalItemPrice);
        order.setInspectionScope(InspectionScope.BUYER_REQUEST);
        order.setOrderType(orderType);
        order.setFeePayer(feePayer);
        order.setStatus(InspectionOrderStatus.WAITING_SELLER_APPROVAL.name());
        order.setApprovalDeadline(Instant.now().plus(Duration.ofHours(approvalTtlHours)));

        order.setInspectionFee(inspectionFee);
        order.setShippingFee(shippingFee);
        order.setTravelDistanceKm(travelDist);
        order.setTravelFee(travelFee);
        order.setReturnShippingReserveFee(returnReserve);
        inspectionPricingService.applyConfiguration(order, quote);

        order.setCashShippingSimulation(orderType == InspectionOrderType.AT_CENTER && quote.cashShippingSimulationEnabled());
        setFeeShares(order, totalFee);
        order.setBuyerCommitmentDeposit(commitmentDeposit);

        order.setBuyerConsentZin(request.buyerConsentZinLoss());
        order.setSellerConsentZin(false);

        if (request.buyerDeliveryAddress() != null) {
            try {
                order.setBuyerDeliveryAddressJson(objectMapper.writeValueAsString(request.buyerDeliveryAddress()));
            } catch (Exception ex) {
                log.warn("Failed to serialize buyer delivery address: {}", ex.getMessage());
            }
        }

        // Đặt bài đăng sang trạng thái RESERVED để tránh người khác đặt mua đè
        post.setStatus("RESERVED");
        postRepository.save(post);

        InspectionOrder saved = inspectionOrderRepository.save(order);
        log.info("Created inspection order {} for post {} by buyer {}", saved.getId(), post.getId(), buyerId);

        if (notificationService != null) {
            notificationService.notify(
                    buyerId,
                    "Yêu cầu kiểm định đã gửi",
                    "Yêu cầu kiểm định cho sản phẩm \"" + post.getTitle() + "\" đã được gửi tới người bán.",
                    NotificationType.INSPECTION,
                    saved.getId(),
                    "INSPECTION_ORDER",
                    "/buyer/inspections/" + saved.getId());
            if (seller != null) {
                notificationService.notify(
                        seller.getId(),
                        "Yêu cầu kiểm định mới",
                        "Có người mua yêu cầu kiểm định cho sản phẩm \"" + post.getTitle()
                                + "\". Vui lòng kiểm tra và phản hồi.",
                        NotificationType.INSPECTION,
                        saved.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + saved.getId());
            }
        }

        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionOrderResponse respondInspection(UUID sellerId, UUID orderId,
            InspectionSellerRespondRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getSeller() == null || !order.getSeller().getId().equals(sellerId)) {
            throw new ForbiddenException("Bạn không phải người bán của đơn kiểm định này");
        }

        if (!InspectionOrderStatus.WAITING_SELLER_APPROVAL.name().equals(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định không ở trạng thái chờ người bán phản hồi");
        }

        String action = request.action() != null ? request.action().toUpperCase() : "";

        if ("REJECT".equals(action)) {
            order.setStatus(InspectionOrderStatus.CANCELLED.name());
            order.setApprovalDeadline(null);
            order.setNote("Người bán từ chối: "
                    + (request.rejectReason() != null ? request.rejectReason() : "Không nêu lý do"));
            restorePostStatusAfterInspectionEnd(order);
            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getBuyer() != null) {
                notificationService.notify(
                        order.getBuyer().getId(),
                        "Người bán từ chối kiểm định",
                        "Người bán đã từ chối yêu cầu kiểm định cho sản phẩm \""
                                + (order.getPost() != null ? order.getPost().getTitle() : "") + "\".",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/buyer/inspections/" + order.getId());
            }
            return inspectionOrderMapper.toResponse(saved);
        }

        if ("COUNTER".equals(action)) {
            if (request.counterFeePayer() == null) {
                throw new BadRequestException("Cần chỉ định phương án phí đề xuất lại (counterFeePayer)");
            }

            if (request.counterOrderType() != null && request.counterOrderType() != order.getOrderType()) {
                order.setOrderType(request.counterOrderType());
                ShippingAddressRequest storedBuyerAddress = parseStoredBuyerAddress(order.getBuyerDeliveryAddressJson());
                BigDecimal buyerLat = storedBuyerAddress != null ? storedBuyerAddress.latitude() : null;
                BigDecimal buyerLng = storedBuyerAddress != null ? storedBuyerAddress.longitude() : null;
                InspectionQuoteResponse quote = inspectionPricingService.calculateQuote(
                        InspectionQuoteRequest.builder()
                                .postId(order.getPost().getId())
                                .buyerLatitude(buyerLat)
                                .buyerLongitude(buyerLng)
                                .inspectionCenterId(order.getInspectionCenter() == null ? null : order.getInspectionCenter().getId())
                                .orderType(request.counterOrderType())
                                .build());
                if (request.counterOrderType() == InspectionOrderType.AT_HOME) {
                    order.setTravelDistanceKm(quote.travelDistanceKm());
                    order.setTravelFee(quote.travelFeeRoundTrip());
                    order.setShippingFee(BigDecimal.ZERO);
                    order.setReturnShippingReserveFee(BigDecimal.ZERO);
                } else {
                    order.setTravelDistanceKm(BigDecimal.ZERO);
                    order.setTravelFee(BigDecimal.ZERO);
                    order.setShippingFee(quote.shippingToCenterFee());
                    order.setReturnShippingReserveFee(quote.returnShippingReserveFee());
                }
                order.setInspectionFee(quote.baseInspectionFee());
                order.setCashShippingSimulation(request.counterOrderType() == InspectionOrderType.AT_CENTER
                        && quote.cashShippingSimulationEnabled());
                inspectionPricingService.applyConfiguration(order, quote);
            }

            order.setFeePayer(request.counterFeePayer());
            BigDecimal totalFee = order.getOrderType() == InspectionOrderType.AT_HOME
                    ? order.getInspectionFee().add(order.getTravelFee())
                    : order.getInspectionFee().add(order.getShippingFee()).add(order.getReturnShippingReserveFee());

            setFeeShares(order, totalFee);
            order.setStatus(InspectionOrderStatus.WAITING_BUYER_APPROVAL.name());
            order.setApprovalDeadline(Instant.now().plus(Duration.ofHours(approvalTtlHours)));
            order.setNote("Người bán đề xuất lại phương án chia phí: " + request.counterFeePayer());
            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getBuyer() != null) {
                notificationService.notify(
                        order.getBuyer().getId(),
                        "Người bán đề xuất lại phí kiểm định",
                        "Người bán đã đề xuất lại phương án kiểm định cho sản phẩm \""
                                + (order.getPost() != null ? order.getPost().getTitle() : "")
                                + "\". Vui lòng phản hồi.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/buyer/inspections/" + order.getId());
            }
            return inspectionOrderMapper.toResponse(saved);
        }

        if ("ACCEPT".equals(action)) {
            if (!request.sellerConsentZinLoss()) {
                throw new BadRequestException(
                        "Người bán bắt buộc phải xác nhận đồng ý tháo máy kiểm định (nguy cơ mất zin)");
            }
            order.setSellerConsentZin(true);
            order.setStatus(InspectionOrderStatus.WAITING_FEE_PAYMENT.name());
            order.setPaymentDeadline(Instant.now().plus(INSPECTION_FEE_PAYMENT_WINDOW));
            order.setApprovalDeadline(null);

            // Nếu bên nào có phí = 0 thì coi như đã thanh toán
            boolean buyerZeroFee = (order.getBuyerFeeShare() == null
                    || order.getBuyerFeeShare().compareTo(BigDecimal.ZERO) == 0)
                    && (order.getBuyerCommitmentDeposit() == null
                            || order.getBuyerCommitmentDeposit().compareTo(BigDecimal.ZERO) == 0);
            if (buyerZeroFee) {
                order.setBuyerPaidAt(Instant.now());
            }
            if (order.getSellerFeeShare() == null || order.getSellerFeeShare().compareTo(BigDecimal.ZERO) == 0) {
                order.setSellerPaidAt(Instant.now());
            }

            boolean buyerDone = buyerZeroFee || order.getBuyerPaidAt() != null;
            boolean sellerDone = (order.getSellerFeeShare() == null
                    || order.getSellerFeeShare().compareTo(BigDecimal.ZERO) == 0) || order.getSellerPaidAt() != null;

            if (buyerDone && sellerDone) {
                order.setStatus(InspectionOrderStatus.CONFIRMED_PENDING_DISPATCH.name());
                notifyCenterManagersPendingDispatch(order);
            }

            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getBuyer() != null) {
                notificationService.notify(
                        order.getBuyer().getId(),
                        "Người bán đã đồng ý kiểm định",
                        "Người bán đã đồng ý yêu cầu kiểm định cho sản phẩm \""
                                + (order.getPost() != null ? order.getPost().getTitle() : "")
                                + "\". Vui lòng thanh toán phí trong vòng " + INSPECTION_FEE_PAYMENT_WINDOW.toHours() + " giờ.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/buyer/inspections/" + order.getId());
            }
            return inspectionOrderMapper.toResponse(saved);
        }

        throw new BadRequestException("Hành động phản hồi không hợp lệ. Chỉ chấp nhận ACCEPT, REJECT hoặc COUNTER");
    }

    @Override
    @Transactional
    public InspectionOrderResponse respondInspectionByBuyer(UUID buyerId, UUID orderId,
            InspectionBuyerRespondRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyerId)) {
            throw new ForbiddenException("Bạn không phải người mua của đơn kiểm định này");
        }

        if (!InspectionOrderStatus.WAITING_BUYER_APPROVAL.name().equals(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định không ở trạng thái chờ người mua phản hồi");
        }

        if (order.getApprovalDeadline() != null && order.getApprovalDeadline().isBefore(Instant.now())) {
            order.setStatus(InspectionOrderStatus.CANCELLED.name());
            order.setNote("Đơn kiểm định đã bị hủy do hết hạn phản hồi từ người mua");
            restorePostStatusAfterInspectionEnd(order);
            inspectionOrderRepository.save(order);
            throw new ConflictException("Thời hạn phản hồi đề xuất kiểm định (24 giờ) đã hết");
        }

        String action = request.action() != null ? request.action().toUpperCase() : "";

        if ("REJECT".equals(action)) {
            order.setStatus(InspectionOrderStatus.CANCELLED.name());
            order.setApprovalDeadline(null);
            order.setNote(
                    "Người mua từ chối đề xuất: " + (request.note() != null ? request.note() : "Không nêu lý do"));
            restorePostStatusAfterInspectionEnd(order);
            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getSeller() != null) {
                notificationService.notify(
                        order.getSeller().getId(),
                        "Người mua từ chối đề xuất chia phí",
                        "Người mua đã từ chối phương án kiểm định bạn đề xuất. Đơn kiểm định đã kết thúc.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId());
            }
            return inspectionOrderMapper.toResponse(saved);
        }

        if ("COUNTER".equals(action)) {
            if (request.counterFeePayer() == null) {
                throw new BadRequestException("Cần chỉ định phương án phí đề xuất lại (counterFeePayer)");
            }

            order.setFeePayer(request.counterFeePayer());
            BigDecimal totalFee = order.getOrderType() == InspectionOrderType.AT_HOME
                    ? order.getInspectionFee().add(order.getTravelFee())
                    : order.getInspectionFee().add(order.getShippingFee()).add(order.getReturnShippingReserveFee());

            setFeeShares(order, totalFee);

            order.setStatus(InspectionOrderStatus.WAITING_SELLER_APPROVAL.name());
            order.setApprovalDeadline(Instant.now().plus(Duration.ofHours(approvalTtlHours)));
            order.setNote("Người mua đề xuất lại phương án chia phí: " + request.counterFeePayer());
            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getSeller() != null) {
                notificationService.notify(
                        order.getSeller().getId(),
                        "Người mua đề xuất lại phí kiểm định",
                        "Người mua đã đề xuất lại phương án kiểm định cho sản phẩm \""
                                + (order.getPost() != null ? order.getPost().getTitle() : "")
                                + "\". Vui lòng phản hồi.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId());
            }
            return inspectionOrderMapper.toResponse(saved);
        }

        if ("ACCEPT".equals(action)) {
            if (request.buyerConsentZin() != null && !request.buyerConsentZin()) {
                throw new BadRequestException("Bạn cần đồng ý với điều khoản tháo máy kiểm định");
            }
            if (request.buyerConsentZin() != null) {
                order.setBuyerConsentZin(request.buyerConsentZin());
            }
            order.setStatus(InspectionOrderStatus.WAITING_FEE_PAYMENT.name());
            order.setPaymentDeadline(Instant.now().plus(INSPECTION_FEE_PAYMENT_WINDOW));
            order.setApprovalDeadline(null);

            boolean buyerZeroFee = (order.getBuyerFeeShare() == null
                    || order.getBuyerFeeShare().compareTo(BigDecimal.ZERO) == 0)
                    && (order.getBuyerCommitmentDeposit() == null
                            || order.getBuyerCommitmentDeposit().compareTo(BigDecimal.ZERO) == 0);
            if (buyerZeroFee) {
                order.setBuyerPaidAt(Instant.now());
            }
            if (order.getSellerFeeShare() == null || order.getSellerFeeShare().compareTo(BigDecimal.ZERO) == 0) {
                order.setSellerPaidAt(Instant.now());
            }

            boolean buyerDone = buyerZeroFee || order.getBuyerPaidAt() != null;
            boolean sellerDone = (order.getSellerFeeShare() == null
                    || order.getSellerFeeShare().compareTo(BigDecimal.ZERO) == 0) || order.getSellerPaidAt() != null;

            if (buyerDone && sellerDone) {
                order.setStatus(InspectionOrderStatus.CONFIRMED_PENDING_DISPATCH.name());
                notifyCenterManagersPendingDispatch(order);
            }

            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getSeller() != null) {
                notificationService.notify(
                        order.getSeller().getId(),
                        "Người mua đã chấp thuận đề xuất kiểm định",
                        "Người mua đã chấp thuận đề xuất chia phí kiểm định cho sản phẩm \""
                                + (order.getPost() != null ? order.getPost().getTitle() : "")
                                + "\". Vui lòng chuẩn bị đóng phí.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId());
            }
            return inspectionOrderMapper.toResponse(saved);
        }

        throw new BadRequestException("Hành động không hợp lệ. Phải là ACCEPT, REJECT hoặc COUNTER");
    }

    @Override
    @Transactional
    public InspectionOrderResponse scheduleAppointment(UUID sellerId, UUID orderId, InspectionAppointmentRequest request) {
        InspectionOrder order = loadAppointmentOrder(orderId);
        requireAppointmentSeller(order, sellerId);
        requireAppointmentEditable(order);
        InspectionAppointmentStatus status = order.getAppointmentStatus();
        if (status != null && status != InspectionAppointmentStatus.REJECTED && status != InspectionAppointmentStatus.COUNTERED_BY_CENTER) {
            throw new ConflictException("Chỉ được gửi lịch khi chưa có đề xuất, lịch đã bị từ chối hoặc đang có đề xuất lịch từ trung tâm");
        }
        User center = requireAppointmentCenter(order);
        InspectionAppointmentShift shift = validateAppointmentSlot(request.scheduledAt(), request.shift());

        order.setAppointmentScheduledAt(request.scheduledAt());
        order.setAppointmentShift(shift);
        order.setAppointmentNote(request.note() == null ? null : request.note().strip());
        order.setAppointmentStatus(InspectionAppointmentStatus.PENDING_CENTER_CONFIRMATION);
        order.setAppointmentRejectionReason(null);
        order.setHomeCheckInCode(null);
        clearAppointmentCounter(order);

        notifyAppointmentCenter(order, center, "Có lịch kiểm định tại nhà cần duyệt",
                "Người bán đề xuất lịch " + localAppointmentTime(request.scheduledAt()) + ".");
        return inspectionOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public InspectionOrderResponse confirmAppointment(UUID actorId, UUID orderId) {
        InspectionOrder order = loadAppointmentOrder(orderId);
        requireAppointmentReviewer(order, actorId);
        requireAppointmentEditable(order);
        requireAppointmentStatus(order, InspectionAppointmentStatus.PENDING_CENTER_CONFIRMATION);
        validateAppointmentSlot(order.getAppointmentScheduledAt(), order.getAppointmentShift());

        confirmAppointmentState(order);
        notifyAppointmentSeller(order, "Lịch kiểm định tại nhà đã được xác nhận",
                "Trung tâm đã chấp nhận lịch hẹn vào " + localAppointmentTime(order.getAppointmentScheduledAt()) + ".");
        return inspectionOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public InspectionOrderResponse counterAppointment(UUID actorId, UUID orderId, InspectionAppointmentCounterRequest request) {
        InspectionOrder order = loadAppointmentOrder(orderId);
        requireAppointmentReviewer(order, actorId);
        requireAppointmentEditable(order);
        requireAppointmentStatus(order, InspectionAppointmentStatus.PENDING_CENTER_CONFIRMATION);
        InspectionAppointmentShift shift = validateAppointmentSlot(request.counterScheduledAt(), request.counterShift());
        if (request.counterScheduledAt().equals(order.getAppointmentScheduledAt())) {
            throw new BadRequestException("Lịch đề xuất phải khác lịch hiện tại của người bán");
        }

        String note = request.counterNote().strip();
        order.setCounterScheduledAt(request.counterScheduledAt());
        order.setCounterShift(shift);
        order.setCounterNote(note);
        order.setAppointmentRejectionReason(note);
        order.setAppointmentStatus(InspectionAppointmentStatus.COUNTERED_BY_CENTER);
        notifyAppointmentSeller(order, "Trung tâm đề xuất lịch kiểm định mới",
                "Lịch đề xuất vào " + localAppointmentTime(request.counterScheduledAt()) + ". Lý do: " + note);
        return inspectionOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public InspectionOrderResponse acceptAppointmentCounter(UUID sellerId, UUID orderId) {
        InspectionOrder order = loadAppointmentOrder(orderId);
        requireAppointmentSeller(order, sellerId);
        requireAppointmentEditable(order);
        requireAppointmentStatus(order, InspectionAppointmentStatus.COUNTERED_BY_CENTER);
        User center = requireAppointmentCenter(order);
        InspectionAppointmentShift shift = validateAppointmentSlot(order.getCounterScheduledAt(), order.getCounterShift());

        order.setAppointmentScheduledAt(order.getCounterScheduledAt());
        order.setAppointmentShift(shift);
        clearAppointmentCounter(order);
        confirmAppointmentState(order);
        notifyAppointmentCenter(order, center, "Người bán đã đồng ý lịch kiểm định",
                "Người bán đã đồng ý lịch " + localAppointmentTime(order.getAppointmentScheduledAt()) + ". Vui lòng phân công KTV.");
        return inspectionOrderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public InspectionOrderResponse rejectAppointment(UUID actorId, UUID orderId, InspectionAppointmentRejectRequest request) {
        InspectionOrder order = loadAppointmentOrder(orderId);
        requireAppointmentReviewer(order, actorId);
        requireAppointmentEditable(order);
        if (order.getAppointmentStatus() != InspectionAppointmentStatus.PENDING_CENTER_CONFIRMATION
                && order.getAppointmentStatus() != InspectionAppointmentStatus.COUNTERED_BY_CENTER) {
            throw new ConflictException("Chỉ được từ chối lịch hẹn đang chờ phản hồi");
        }

        String reason = request.reason().strip();
        order.setAppointmentStatus(InspectionAppointmentStatus.REJECTED);
        order.setAppointmentRejectionReason(reason);
        clearAppointmentCounter(order);
        notifyAppointmentSeller(order, "Vui lòng đặt lại lịch kiểm định tại nhà",
                "Trung tâm đã từ chối lịch hẹn. Lý do: " + reason);
        return inspectionOrderMapper.toResponse(order);
    }

    private InspectionOrder loadAppointmentOrder(UUID orderId) {
        return inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));
    }

    private void requireAppointmentSeller(InspectionOrder order, UUID sellerId) {
        if (order.getSeller() == null || !sellerId.equals(order.getSeller().getId())) {
            throw new ForbiddenException("Chỉ người bán của đơn kiểm định mới được phản hồi lịch hẹn");
        }
    }

    private User requireAppointmentCenter(InspectionOrder order) {
        User center = order.getInspectionCenter();
        if (center == null && order.getInspector() != null) {
            center = order.getInspector().getManager();
        }
        if (center == null) {
            throw new ConflictException("Đơn chưa xác định trung tâm phụ trách lịch kiểm định");
        }
        return center;
    }

    private void requireAppointmentReviewer(InspectionOrder order, UUID actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ForbiddenException("Tài khoản không có quyền duyệt lịch"));
        if (!actor.hasRole("ADMIN") && (!actor.hasRole("INSPECTION_CENTER")
                || !actorId.equals(requireAppointmentCenter(order).getId()))) {
            throw new ForbiddenException("Chỉ trung tâm phụ trách đơn hoặc Admin mới được duyệt lịch");
        }
    }

    private void requireAppointmentEditable(InspectionOrder order) {
        if (order.getOrderType() != InspectionOrderType.AT_HOME) {
            throw new BadRequestException("Lịch hẹn chỉ áp dụng cho kiểm định tại nhà (AT_HOME)");
        }
        if (!APPOINTMENT_ORDER_STATES.contains(order.getStatus()) || order.getHomeCheckedInAt() != null) {
            throw new ConflictException("Đơn chưa sẵn sàng đặt lịch hoặc đã bắt đầu kiểm định");
        }
        requireInspectionFeesPaid(order);
    }

    private void requireAppointmentStatus(InspectionOrder order, InspectionAppointmentStatus expected) {
        if (order.getAppointmentStatus() != expected) {
            throw new ConflictException("Lịch hẹn không còn ở trạng thái cho phép thao tác");
        }
    }

    private InspectionAppointmentShift validateAppointmentSlot(Instant time, InspectionAppointmentShift shift) {
        if (time == null || !time.isAfter(Instant.now())) {
            throw new BadRequestException("Lịch hẹn kiểm định phải ở thời điểm tương lai");
        }
        LocalTime localTime = time.atZone(INSPECTION_TIME_ZONE).toLocalTime();
        if (shift == null) {
            shift = InspectionAppointmentShift.MORNING.contains(localTime)
                    ? InspectionAppointmentShift.MORNING : InspectionAppointmentShift.AFTERNOON;
        }
        if (!shift.contains(localTime)) {
            throw new BadRequestException("Giờ hẹn phải nằm trong ca đã chọn (08:00–12:00 hoặc 13:00–17:00 theo giờ Việt Nam)");
        }
        return shift;
    }

    private void clearAppointmentCounter(InspectionOrder order) {
        order.setCounterScheduledAt(null);
        order.setCounterShift(null);
        order.setCounterNote(null);
    }

    private void confirmAppointmentState(InspectionOrder order) {
        order.setAppointmentStatus(InspectionAppointmentStatus.CONFIRMED);
        order.setHomeCheckInCode(null);
        order.setStatus(InspectionOrderStatus.SCHEDULED.name());
    }

    private String localAppointmentTime(Instant time) {
        return time.atZone(INSPECTION_TIME_ZONE).toOffsetDateTime().toString();
    }

    private void notifyAppointmentCenter(InspectionOrder order, User center, String title, String message) {
        notificationService.notifyInApp(center.getId(), title, message, NotificationType.INSPECTION, order.getId(),
                "INSPECTION_ORDER", "/inspection-center/orders");
    }

    private void notifyAppointmentSeller(InspectionOrder order, String title, String message) {
        notificationService.notifyInApp(order.getSeller().getId(), title, message, NotificationType.INSPECTION, order.getId(),
                "INSPECTION_ORDER", "/seller/inspections/" + order.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionOrderResponse getOrderById(UUID actorId, UUID orderId) {
        InspectionOrder order = inspectionOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        boolean isBuyer = order.getBuyer() != null && order.getBuyer().getId().equals(actorId);
        boolean isSeller = order.getSeller() != null && order.getSeller().getId().equals(actorId);
        boolean isInspector = order.getInspector() != null && order.getInspector().getId().equals(actorId);

        User actor = userRepository.findById(actorId).orElse(null);
        boolean isStaffOrAdmin = actor != null && actor.getUserRoles().stream()
                .anyMatch(ur -> Set.of("ADMIN", "STAFF").contains(ur.getRole().getCode()));
        boolean isCenter = order.getInspectionCenter() != null && actorId.equals(order.getInspectionCenter().getId());
        boolean managesInspector = order.getInspector() != null && order.getInspector().getManager() != null
                && actorId.equals(order.getInspector().getManager().getId());

        boolean legacyCenterManager = order.getInspectionCenter() == null && managesInspector;
        if (!isBuyer && !isSeller && !isInspector && !isStaffOrAdmin && !isCenter && !legacyCenterManager) {
            throw new ForbiddenException("Bạn không có quyền xem thông tin đơn kiểm định này");
        }

        boolean canViewCenterPayout = actor != null && actor.hasRole("INSPECTION_CENTER")
                && (isCenter || legacyCenterManager);
        BigDecimal centerPayoutAmount = canViewCenterPayout
                ? feeSettlementService.getCenterPayouts(actorId, List.of(orderId)).get(orderId)
                : null;
        return toOrderDetail(order, centerPayoutAmount);
    }

    private InspectionOrderResponse toOrderDetail(InspectionOrder inspection, BigDecimal centerPayoutAmount) {
        Order purchase = inspection.getOrder();
        List<Shipment> route = shipments.findInspectionRoute(inspection.getId(), purchase == null ? null : purchase.getId());
        Shipment inbound = latestShipment(route, "SELLER_TO_CENTER");
        Shipment delivery = latestShipment(route, "CENTER_TO_BUYER");
        Shipment returning = latestShipment(route, "CENTER_TO_SELLER");

        ShippingAddress sellerContact = shipmentContact(inbound, true);
        if (sellerContact == null) sellerContact = shipmentContact(returning, false);

        ShippingAddress centerContact = shipmentContact(inbound, false);
        if (centerContact == null) centerContact = shipmentContact(delivery, true);
        if (centerContact == null) centerContact = shipmentContact(returning, true);

        if ((sellerContact == null || centerContact == null) && inspection.getInspectionShippingQuoteId() != null) {
            var quote = inspectionShippingQuotes.findById(inspection.getInspectionShippingQuoteId()).orElse(null);
            if (quote != null) {
                var quotedRoute = objectMapper.readTree(quote.getOutboundPayload());
                if (sellerContact == null) sellerContact = ShippingPayloads.storedAddress(quotedRoute, "from");
                if (centerContact == null) centerContact = ShippingPayloads.storedAddress(quotedRoute, "to");
            }
        }

        User seller = inspection.getSeller() != null ? inspection.getSeller() : inspection.getPost().getUser();
        if (sellerContact == null && seller != null) sellerContact = shippingQuoteService.pickup(seller.getId());
        if (centerContact == null && inspection.getInspectionCenter() != null) {
            centerContact = inspectionCenterProfileMapper.toShippingAddress(inspection.getInspectionCenter().getProfile());
        }

        ShippingAddress buyerContact = shipmentContact(delivery, false);
        if (buyerContact == null && purchase != null) buyerContact = readShippingContact(purchase.getDeliveryAddress());
        if (buyerContact == null) buyerContact = readShippingContact(inspection.getBuyerDeliveryAddressJson());

        return inspectionOrderMapper.toResponse(inspection, withProfilePhone(sellerContact, seller),
                withProfilePhone(buyerContact, inspection.getBuyer()), centerContact, centerPayoutAmount);
    }

    private Shipment latestShipment(List<Shipment> route, String leg) {
        return route.reversed().stream().filter(shipment -> leg.equals(shipment.getLeg())).findFirst().orElse(null);
    }

    private ShippingAddress shipmentContact(Shipment shipment, boolean sender) {
        if (shipment == null) return null;
        String address = sender ? shipment.getSenderAddress() : shipment.getReceiverAddress();
        if (!StringUtils.hasText(address)) return null;
        return sender
                ? new ShippingAddress(shipment.getSenderName(), shipment.getSenderPhone(), address, null, null)
                : new ShippingAddress(shipment.getReceiverName(), shipment.getReceiverPhone(), address, null, null);
    }

    private ShippingAddress readShippingContact(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            return objectMapper.readValue(json, ShippingAddress.class);
        } catch (JacksonException exception) {
            log.warn("Invalid stored inspection delivery address");
            return null;
        }
    }

    private ShippingAddress withProfilePhone(ShippingAddress contact, User user) {
        if (user == null || user.getProfile() == null || (contact != null && StringUtils.hasText(contact.phone()))) {
            return contact;
        }
        String phone = user.getProfile().getPhone();
        if (contact == null) return new ShippingAddress(null, phone, null, null, null);
        return new ShippingAddress(contact.name(), phone, contact.address(), contact.latitude(), contact.longitude());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getBuyerOrders(UUID buyerId, Pageable pageable) {
        return inspectionOrderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)
                .map(inspectionOrderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getSellerOrders(UUID sellerId, Pageable pageable) {
        return inspectionOrderRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable)
                .map(inspectionOrderMapper::toResponse);
    }

    @Override
    @Transactional
    public InspectionOrderResponse confirmFinalPurchase(UUID buyerId, UUID orderId) {
        return confirmFinalPurchase(buyerId, orderId, null);
    }

    @Override
    @Transactional
    public InspectionOrderResponse confirmFinalPurchase(UUID buyerId, UUID orderId,
            ConfirmInspectionPurchaseRequest request) {
        Post post = lockPostForFinalDecision(orderId);

        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyerId)) {
            throw new ForbiddenException("Bạn không phải người mua của đơn kiểm định này");
        }

        // Idempotency: retry call after completion returns existing response
        if (order.getOrder() != null && order.getFinalPaymentPaidAt() != null) {
            return inspectionOrderMapper.toResponse(order);
        }

        if (!InspectionOrderStatus.PASSED.name().equals(order.getStatus())) {
            throw new ConflictException(
                    "Đơn kiểm định phải ở trạng thái ĐẠT CHUẨN (PASSED) mới có thể xác nhận mua hàng");
        }

        if (order.getFinalPaymentDeadline() != null && order.getFinalPaymentDeadline().isBefore(Instant.now())) {
            throw new ConflictException("Thời hạn thanh toán tiền hàng (6 giờ) đã kết thúc");
        }

        if (order.getFinalPaymentPaidAt() != null) {
            throw new ConflictException("Đơn hàng đã được thanh toán tiền hàng rồi");
        }

        BigDecimal itemPrice = order.getFinalItemPrice() != null ? order.getFinalItemPrice() : post.getPrice();

        // Step 1: Settle inspection fees and refund unused return shipping reserve
        feeSettlementService.settle(order, InspectionSettlementOutcome.PASSED_PURCHASED,
                "Buyer confirmed purchase after PASSED inspection");

        // Step 2: Resolve shipping quote (use passed quoteId or auto-quote from buyer
        // delivery address)
        UUID quoteId = request != null ? request.shippingQuoteId() : null;
        if (quoteId == null) {
            ShippingAddressRequest deliveryAddressRequest = request != null && request.deliveryAddress() != null
                    ? request.deliveryAddress()
                    : parseStoredBuyerAddress(order.getBuyerDeliveryAddressJson());
            if (deliveryAddressRequest == null) {
                throw new BadRequestException(
                        "shippingQuoteId hoặc địa chỉ nhận hàng (deliveryAddress) là bắt buộc để hoàn tất mua hàng");
            }
            ShippingQuoteResponse quoteResponse = shippingQuoteService.quote(buyerId,
                    new ShippingQuoteRequest(post.getId(), null, deliveryAddressRequest));
            quoteId = quoteResponse.quoteId();
        }

        // Step 3: Create and complete canonical Order holding funds in SystemWallet
        UUID reqId = (request != null && request.requestId() != null)
                ? request.requestId()
                : UUID.nameUUIDFromBytes(("INSPECTION_ORDER:" + order.getId()).getBytes(StandardCharsets.UTF_8));
        OrderPaymentMethod paymentMethod = (request != null && request.paymentMethod() != null)
                ? request.paymentMethod()
                : OrderPaymentMethod.WALLET;

        Order newOrder = new Order();
        newOrder.setPost(post);
        newOrder.setBuyer(order.getBuyer());
        newOrder.setSeller(order.getSeller() != null ? order.getSeller() : post.getUser());
        newOrder.setNegotiation(order.getNegotiation());
        newOrder.setFinalPrice(itemPrice);
        newOrder.setRequestId(reqId);
        newOrder.setPaymentMethod(paymentMethod);
        newOrder.setStatus(OrderStatus.PENDING_PAYMENT);
        newOrder.setEscrowStatus(EscrowStatus.PENDING);
        newOrder = orderRepository.saveAndFlush(newOrder);

        commissions.capture(newOrder, buyerId, "Inspection PASSED purchase confirmed");
        ShippingQuote quote = shippingQuoteService.consume(buyerId, post.getId(), quoteId, newOrder.getId(), itemPrice);
        if (order.getInspectionShippingQuoteId() != null && !"CENTER_TO_BUYER".equals(quote.getLeg())) {
            throw new ConflictException("Cần báo giá giao hàng từ trung tâm đến người mua để thanh toán");
        }
        newOrder.setShippingQuoteId(quote.getId());
        newOrder.setShippingFee(quote.getFee());
        newOrder.setDeliveryAddress(quote.getDeliveryAddress());

        paidOrderService.complete(newOrder, true);

        // Auto-mark seller confirmed since seller consented to inspection and goods are
        // verified
        newOrder.setSellerConfirmedAt(newOrder.getPaidAt());
        newOrder.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(newOrder);

        order.setOrder(newOrder);
        order.setFinalShippingFee(quote.getFee());
        order.setFinalPaymentPaidAt(Instant.now());
        order.setStatus(InspectionOrderStatus.DELIVERING_TO_BUYER.name());

        InspectionOrder savedOrder = inspectionOrderRepository.save(order);

        if (notificationService != null) {
            String title = post.getTitle() != null ? post.getTitle() : "";
            notificationService.notifyWithEmail(
                    buyerId,
                    "Thanh toán mua hàng thành công",
                    "Bạn đã hoàn tất thanh toán mua sản phẩm \"" + title
                            + "\" sau kiểm định. Đơn hàng đang được chuẩn bị giao.",
                    NotificationType.ORDER,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/buyer/orders/" + newOrder.getId());
            if (order.getSeller() != null) {
                notificationService.notifyWithEmail(
                        order.getSeller().getId(),
                        "Khách hàng đã thanh toán mua sản phẩm",
                        "Người mua đã hoàn tất thanh toán cho sản phẩm \"" + title + "\" sau kiểm định.",
                        NotificationType.ORDER,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/orders/" + newOrder.getId());
            }
        }

        return inspectionOrderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public InspectionOrderResponse rejectFinalPurchase(UUID buyerId, UUID orderId,
            InspectionRejectPurchaseRequest request) {
        lockPostForFinalDecision(orderId);
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyerId)) {
            throw new ForbiddenException("Bạn không phải người mua của đơn kiểm định này");
        }

        if (!InspectionOrderStatus.PASSED.name().equals(order.getStatus())) {
            throw new ConflictException(
                    "Đơn kiểm định phải ở trạng thái ĐẠT CHUẨN (PASSED) mới có thể từ chối mua tiếp");
        }

        if (order.getFinalPaymentPaidAt() != null) {
            throw new ConflictException("Đơn hàng đã được thanh toán tiền hàng rồi, không thể từ chối");
        }

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "Người mua từ chối mua tiếp sau khi có kết quả kiểm định đạt chuẩn";

        order.setStatus(InspectionOrderStatus.CANCELLED.name());
        order.setNote((order.getNote() != null ? order.getNote() + " | " : "") +
                "Người mua từ chối mua tiếp sau kiểm định. Lý do: " + reason);

        // Hạch toán phí kiểm định, ship hoàn và bồi thường cọc cam kết
        feeSettlementService.settle(order, InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED, reason);

        restorePostStatusAfterInspectionEnd(order);

        InspectionOrder savedOrder = inspectionOrderRepository.save(order);

        if (notificationService != null) {
            String title = order.getPost() != null && order.getPost().getTitle() != null ? order.getPost().getTitle()
                    : "";
            if (order.getSeller() != null) {
                notificationService.notifyWithEmail(
                        order.getSeller().getId(),
                        "Người mua từ chối mua tiếp sau kiểm định",
                        "Người mua đã từ chối mua sản phẩm \"" + title
                                + "\" sau kiểm định. Tiền cọc cam kết đã được bồi thường vào ví của bạn (nếu có).",
                        NotificationType.ORDER,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId());
            }
            notificationService.notify(
                    buyerId,
                    "Đã hủy đơn mua sau kiểm định",
                    "Bạn đã từ chối mua tiếp sản phẩm \"" + title + "\". Đơn kiểm định đã kết thúc.",
                    NotificationType.ORDER,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/buyer/inspections/" + order.getId());
        }

        return inspectionOrderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionOrderResponse> getMyOrders(UUID inspectorId) {
        return inspectionOrderRepository.findByInspectorIdOrderByCreatedAtDesc(inspectorId).stream()
                .map(inspectionOrderMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getAllOrders(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return inspectionOrderRepository.findByStatus(status.trim().toUpperCase(), pageable)
                    .map(inspectionOrderMapper::toResponse);
        }
        return inspectionOrderRepository.findAll(pageable)
                .map(inspectionOrderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getAllOrders(UUID actorId, String status, Pageable pageable) {
        User actor = userRepository.findById(actorId).orElseThrow(() -> new NotFoundException("User not found"));
        if (actor.hasRole("ADMIN") || actor.hasRole("STAFF")) return getAllOrders(status, pageable);
        String normalized = status == null || status.isBlank() ? null : status.strip().toUpperCase(Locale.ROOT);
        if (actor.hasRole("INSPECTION_CENTER")) {
            Page<InspectionOrder> orders = inspectionOrderRepository.findVisibleToCenter(actorId, normalized, pageable);
            List<UUID> orderIds = orders.getContent().stream().map(InspectionOrder::getId).toList();
            Map<UUID, BigDecimal> payouts = feeSettlementService.getCenterPayouts(actorId, orderIds);
            return orders.map(order -> inspectionOrderMapper.toResponse(order, payouts.get(order.getId())));
        }
        if (actor.hasRole("INSPECTOR")) {
            return inspectionOrderRepository.findVisibleToInspector(actorId, normalized, pageable).map(inspectionOrderMapper::toResponse);
        }
        throw new ForbiddenException("Bạn không có quyền xem danh sách đơn kiểm định");
    }

    @Override
    @Transactional
    public InspectionOrderResponse assignInspector(UUID orderId, UUID inspectorId) {
        return assignInspector(orderId, inspectorId, null);
    }

    @Override
    @Transactional
    public InspectionOrderResponse assignInspector(UUID orderId, UUID inspectorId, UUID managerId) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        User inspector = userRepository.findById(inspectorId)
                .orElseThrow(() -> new NotFoundException("Kỹ thuật viên không tồn tại"));

        boolean platformAssigns = managerId != null && userRepository.findById(managerId)
                .map(actor -> actor.hasRole("ADMIN") || actor.hasRole("STAFF")).orElse(false);
        if (managerId != null && !platformAssigns) {
            if (order.getInspectionCenter() != null && !managerId.equals(order.getInspectionCenter().getId())) {
                throw new ForbiddenException("Đơn kiểm định thuộc trung tâm khác");
            }
            if (inspector.getManager() == null || !inspector.getManager().getId().equals(managerId)) {
                throw new ForbiddenException("Kỹ thuật viên này không thuộc quyền quản lý của bạn");
            }
        }

        boolean hasInspectorRole = inspector.getUserRoles().stream()
                .anyMatch(ur -> "INSPECTOR".equals(ur.getRole().getCode()));
        if (!hasInspectorRole) {
            throw new BadRequestException("Người dùng được chỉ định không phải là Kỹ thuật viên (INSPECTOR)");
        }
        if (order.getInspectionCenter() != null && (inspector.getManager() == null
                || !order.getInspectionCenter().getId().equals(inspector.getManager().getId()))) {
            throw new ForbiddenException("Kỹ thuật viên phải thuộc trung tâm đã chốt trong đơn");
        }
        requireEditableInspectionOrder(order);
        requireFundedConfiguredRequest(order);
        if (order.getChecklistSnapshotJson() != null && order.getInspectionScope() == InspectionScope.BUYER_REQUEST
                && !Set.of("CONFIRMED_PENDING_DISPATCH", "DISPATCHED", "SCHEDULED", "SHIPPING_TO_CENTER", "INSPECTING").contains(order.getStatus())) {
            throw new ConflictException("Đơn chưa sẵn sàng để phân công kiểm định");
        }

        if (order.getOrderType() == InspectionOrderType.AT_HOME) {
            requireAppointmentStatus(order, InspectionAppointmentStatus.CONFIRMED);
        }
        boolean firstAssignment = order.getInspector() == null;
        if (order.getOrderType() == InspectionOrderType.AT_HOME
                && (firstAssignment || !inspectorId.equals(order.getInspector().getId()))) {
            if (order.getHomeCheckedInAt() != null) {
                throw new ConflictException("Kỹ thuật viên đã check-in; không thể đổi người phụ trách");
            }
            order.setHomeCheckInCode(null);
        }
        order.setInspector(inspector);
        if (!InspectionOrderStatus.INSPECTING.name().equals(order.getStatus())) {
            order.setStatus(InspectionOrderStatus.DISPATCHED.name());
        }

        InspectionOrder saved = inspectionOrderRepository.save(order);
        log.info("Assigned inspector {} to inspection order {}", inspectorId, orderId);

        if (notificationService != null) {
            notificationService.notify(
                    inspectorId,
                    "Đơn kiểm định mới được phân công",
                    "Bạn đã được phân công thực hiện kiểm định cho sản phẩm \""
                            + (order.getPost() != null ? order.getPost().getTitle() : "") + "\".",
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/inspector/orders/" + order.getId());

            if (firstAssignment) {
                String title = order.getOrderType() == InspectionOrderType.AT_HOME
                        ? "Đã phân công kỹ thuật viên kiểm định" : "Đơn đang kiểm định";
                String message = "Trung tâm đã phân công kỹ thuật viên cho đơn kiểm định.";
                if (order.getBuyer() != null) {
                    notificationService.notifyInApp(order.getBuyer().getId(), title, message,
                            NotificationType.INSPECTION, order.getId(), "INSPECTION_ORDER",
                            "/buyer/inspections/" + order.getId());
                }
                if (order.getSeller() != null) {
                    notificationService.notifyInApp(order.getSeller().getId(), title, message,
                            NotificationType.INSPECTION, order.getId(), "INSPECTION_ORDER",
                            "/seller/inspections/" + order.getId());
                }
            }
        }

        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionCheckInCodeResponse getOrCreateHomeCheckInCode(UUID sellerId, UUID orderId) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));
        if (order.getSeller() == null || !sellerId.equals(order.getSeller().getId())) {
            throw new ForbiddenException("Chỉ người bán của đơn kiểm định mới được lấy mã check-in");
        }
        requireHomeCheckInReady(order);
        if (order.getHomeCheckInCode() == null) {
            order.setHomeCheckInCode(UUID.randomUUID());
        }
        return new InspectionCheckInCodeResponse(order.getHomeCheckInCode(), order.getAppointmentScheduledAt());
    }

    private void requireHomeCheckInReady(InspectionOrder order) {
        if (order.getOrderType() != InspectionOrderType.AT_HOME) {
            throw new ConflictException("Mã check-in chỉ áp dụng cho kiểm định tại nhà");
        }
        if (!Set.of("SCHEDULED", "DISPATCHED").contains(order.getStatus())
                || order.getAppointmentStatus() != InspectionAppointmentStatus.CONFIRMED
                || order.getAppointmentScheduledAt() == null || order.getInspector() == null) {
            throw new ConflictException("Đơn cần có lịch hẹn và kỹ thuật viên được phân công để check-in");
        }
        requireInspectionFeesPaid(order);
    }

    @Override
    @Transactional
    public InspectionOrderResponse startInspection(UUID actorId, UUID orderId, InspectionStartRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        requireEditableInspectionOrder(order);
        requireFundedConfiguredRequest(order);
        boolean alreadyStarted = InspectionOrderStatus.INSPECTING.name().equals(order.getStatus());
        if (alreadyStarted && order.getInspector() != null && actorId.equals(order.getInspector().getId())) {
            return inspectionOrderMapper.toResponse(order);
        }
        if (order.getOrderType() == InspectionOrderType.AT_HOME) {
            requireAssignedHomeInspector(order, actorId);
            requireHomeCheckInReady(order);
            if (!matchesHomeCheckInCode(order, request)) {
                throw new BadRequestException("Mã check-in của người bán không hợp lệ");
            }
            order.setHomeCheckedInAt(Instant.now());
            order.setHomeCheckInCode(null);
        } else {
            assignActorForInspectionWork(order, actorId);
            if (order.getChecklistSnapshotJson() != null
                    && !Set.of("DISPATCHED", "SCHEDULED", "INSPECTING").contains(order.getStatus())) {
                throw new ConflictException("Đơn chưa được phân công hoặc đã kết thúc kiểm định");
            }
        }

        order.setStatus(InspectionOrderStatus.INSPECTING.name());
        InspectionOrder saved = inspectionOrderRepository.save(order);

        if (notificationService != null) {
            if (order.getBuyer() != null) {
                notificationService.notify(
                        order.getBuyer().getId(),
                        "Kỹ thuật viên bắt đầu kiểm định",
                        "Kỹ thuật viên đang tiến hành kiểm tra sản phẩm \""
                                + (order.getPost() != null ? order.getPost().getTitle() : "") + "\".",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/buyer/inspections/" + order.getId());
            }
            if (order.getSeller() != null) {
                notificationService.notify(
                        order.getSeller().getId(),
                        "Kỹ thuật viên bắt đầu kiểm định",
                        "Kỹ thuật viên đang tiến hành kiểm tra sản phẩm \""
                                + (order.getPost() != null ? order.getPost().getTitle() : "") + "\".",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId());
            }
        }

        return inspectionOrderMapper.toResponse(saved);
    }

    private boolean matchesHomeCheckInCode(InspectionOrder order, InspectionStartRequest request) {
        if (request == null) {
            return true;
        }

        String orderId = order.getId().toString();
        String shortId = orderId.substring(0, 8);
        Set<String> validCodes = new HashSet<>();
        validCodes.add(orderId);
        validCodes.add(shortId);
        validCodes.add("sl-athome-" + shortId);

        UUID sellerCode = order.getHomeCheckInCode();
        if (sellerCode != null) {
            String sellerCodeStr = sellerCode.toString();
            String sellerCodeShort = sellerCodeStr.length() >= 8 ? sellerCodeStr.substring(0, 8) : sellerCodeStr;
            validCodes.add(sellerCodeStr);
            validCodes.add(sellerCodeShort);
            validCodes.add("sl-athome-" + sellerCodeShort);
        }

        List<String> inputs = Stream.of(request.checkInCode(), request.orderCode(), request.barcodeInput())
                .filter(StringUtils::hasText)
                .map(code -> code.strip().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", ""))
                .toList();

        if (inputs.isEmpty()) {
            return true;
        }

        for (String input : inputs) {
            for (String valid : validCodes) {
                String cleanValid = valid.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
                if (input.equals(cleanValid) || input.contains(cleanValid) || cleanValid.contains(input)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void requireEditableInspectionOrder(InspectionOrder order) {
        if (Set.of("PASSED", "FAILED", "COMPLETED", "DELIVERING_TO_BUYER", "DISPUTED",
                "CANCELLED", "PAYMENT_EXPIRED").contains(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định đã kết thúc; không thể chỉnh sửa hồ sơ kiểm định");
        }
    }

    @Override
    @Transactional
    public InspectionOrderResponse submitResult(UUID orderId, UUID inspectorId, InspectionResultRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if ("PASSED".equals(order.getStatus()) && "PASSED".equals(request.status())) {
            requireInspectionWorkActor(order, inspectorId);
            return inspectionOrderMapper.toResponse(order);
        }
        if (order.getOrderType() == InspectionOrderType.AT_HOME) {
            requireAssignedHomeInspector(order, inspectorId);
            if (!InspectionOrderStatus.INSPECTING.name().equals(order.getStatus())) {
                throw new ConflictException("Kỹ thuật viên cần quét mã check-in trước khi nộp biên bản tại nhà");
            }
        } else {
            assignActorForInspectionWork(order, inspectorId);
        }

        if (!Set.of(InspectionOrderStatus.INSPECTING.name(), "SCHEDULED", "DISPATCHED", "WAITING_INSPECTION", "CONFIRMED_PENDING_DISPATCH", "RECEIVED_AT_CENTER", "AWAITING_INSPECTION").contains(order.getStatus())) {
            throw new ConflictException(
                    "Đơn kiểm định chưa ở trạng thái phù hợp để nộp biên bản: " + order.getStatus());
        }

        order.setNote(request.note());
        requireFundedConfiguredRequest(order);
        order.setConditionScore(request.conditionScore());
        order.setConditionGrade(request.conditionGrade());

        storeInspectionPhotos(order, request.evidencePhotoUrls());
        if (order.getOrderType() != InspectionOrderType.AT_CENTER
                && request.tamperSealId() != null && !request.tamperSealId().isBlank()) {
            order.setTamperSealId(request.tamperSealId().trim());
        }

        if (request.videoProofUrl() != null && !request.videoProofUrl().isBlank()) {
            order.setVideoProofUrl(request.videoProofUrl());
            order.setVideoProofPublicId(request.videoProofPublicId());
        }
        if (request.unboxingVideoUrl() != null && !request.unboxingVideoUrl().isBlank()) {
            order.setUnboxingVideoUrl(request.unboxingVideoUrl());
            order.setUnboxingVideoPublicId(request.unboxingVideoPublicId());
        }

        boolean isExternalVideo = (order.getVideoProofUrl() != null
                && !order.getVideoProofUrl().contains("res.cloudinary.com"))
                || (order.getUnboxingVideoUrl() != null && !order.getUnboxingVideoUrl().contains("res.cloudinary.com"));

        if (isExternalVideo) {
            order.setVideoCleanupStatus("EXEMPTED");
            order.setVideoRetentionUntil(null);
        } else {
            order.setVideoCleanupStatus("ACTIVE");
        }

        List<ChecklistItemResultDto> checklistResults = request.checklistResults();
        if (order.getChecklistSnapshotJson() != null) {
            checklistResults = orderChecklists.validateResults(order, checklistResults, request.status());
        }
        if (checklistResults != null && !checklistResults.isEmpty()) {
            order.setChecklistResultJson(objectMapper.writeValueAsString(checklistResults));
        }

        Post post = postRepository.findByIdForUpdate(order.getPost().getId())
                .orElseThrow(() -> new NotFoundException("Bài đăng không tồn tại"));

        if ("FAILED".equals(request.status())) {
            order.setStatus(InspectionOrderStatus.FAILED.name());
            order.setAppealDeadline(Instant.now().plus(Duration.ofHours(24)));
            order.setAppealStatus(InspectionAppealStatus.NONE);
            order.setReputationPenaltyApplied(false);
            if (!isExternalVideo) {
                order.setVideoRetentionUntil(Instant.now().plus(14, ChronoUnit.DAYS));
            }

            if (order.getInspectionScope() == InspectionScope.PRE_LISTING) {
                post.setStatus("REJECTED");
                post.setRejectionReason("Kiểm định chất lượng đầu vào không đạt: "
                        + (request.note() != null ? request.note() : "Không đạt tiêu chuẩn sàn"));
                postRepository.save(post);
            } else {
                feeSettlementService.settle(order, InspectionSettlementOutcome.FAILED_INSPECTION, request.note());
                restorePostStatusAfterInspectionEnd(order);
            }

            if (notificationService != null) {
                String title = post.getTitle();
                if (order.getBuyer() != null) {
                    notificationService.notifyWithEmail(
                            order.getBuyer().getId(),
                            "[Biên bản] Kết quả kiểm định KHÔNG ĐẠT - " + title,
                            "Sản phẩm \"" + title + "\" KHÔNG ĐẠT chuẩn kiểm định. Đơn kiểm định đã kết thúc.",
                            NotificationType.INSPECTION,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/buyer/inspections/" + order.getId());
                }
                if (order.getSeller() != null) {
                    notificationService.notifyWithEmail(
                            order.getSeller().getId(),
                            "[Biên bản] Kết quả kiểm định KHÔNG ĐẠT - " + title,
                            "Sản phẩm \"" + title + "\" KHÔNG ĐẠT chuẩn kiểm định. Bạn có 24 giờ để gửi khiếu nại trước khi hệ thống trừ điểm uy tín.",
                            NotificationType.INSPECTION,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/seller/inspections/" + order.getId());
                }
            }
        } else if ("PASSED".equals(request.status())) {
            order.setStatus(InspectionOrderStatus.PASSED.name());
            if (order.getFinalItemPrice() == null) {
                order.setFinalItemPrice(post.getPrice());
            }

            if (order.getInspectionScope() == InspectionScope.PRE_LISTING) {
                if ("PENDING_INSPECTION".equals(post.getStatus()) || "PENDING_APPROVAL".equals(post.getStatus())) {
                    String scanResult = aiScanPost(post);
                    if ("APPROVED".equals(scanResult)) {
                        publication.activate(post);
                    } else {
                        String reason = scanResult.contains(":")
                                ? scanResult.substring(scanResult.indexOf(":") + 1).trim()
                                : "AI final check failed";
                        post.setStatus("REJECTED");
                        post.setRejectionReason("Post failed final AI scan after inspection: " + reason);
                        postRepository.save(post);
                    }
                }
                order.setStatus(InspectionOrderStatus.COMPLETED.name());
            } else {
                order.setFinalPaymentDeadline(Instant.now().plus(Duration.ofHours(6)));
            }

            if (notificationService != null) {
                String title = post.getTitle();
                if (order.getBuyer() != null) {
                    notificationService.notifyWithEmail(
                            order.getBuyer().getId(),
                            "[Biên bản] Kết quả kiểm định ĐẠT - " + title,
                            "Sản phẩm \"" + title
                                    + "\" đã được kiểm định ĐẠT CHUẨN. Bạn có 6 giờ để xác nhận thanh toán mua hàng.",
                            NotificationType.INSPECTION,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/buyer/inspections/" + order.getId());
                }
                if (order.getSeller() != null) {
                    notificationService.notifyWithEmail(
                            order.getSeller().getId(),
                            "[Biên bản] Kết quả kiểm định ĐẠT - " + title,
                            "Sản phẩm \"" + title + "\" đã được kiểm định ĐẠT CHUẨN.",
                            NotificationType.INSPECTION,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/seller/inspections/" + order.getId());
                }
            }
        } else {
            throw new BadRequestException("Trạng thái kết quả không hợp lệ. Phải là PASSED hoặc FAILED");
        }

        return inspectionOrderMapper.toResponse(inspectionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public void createInspectorAccount(String email, String fullName, String password, UUID managerId) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email đã được đăng ký trong hệ thống");
        }

        Role inspectorRole = roleRepository.findByCode("INSPECTOR")
                .orElseThrow(() -> new RuntimeException("Role INSPECTOR không tồn tại trong database"));

        User manager = managerId != null ? userRepository.findById(managerId).orElse(null) : null;

        User newInspector = new User();
        newInspector.setEmail(email);
        newInspector.setPasswordHash(passwordEncoder.encode(password));
        newInspector.setAccountStatus(AccountStatus.ACTIVE);
        newInspector.setEmailVerified(true);
        newInspector.setManager(manager);

        UserProfile profile = new UserProfile(newInspector, fullName != null && !fullName.isBlank() ? fullName : email,
                null, null);
        newInspector.setProfile(profile);

        newInspector = userRepository.save(newInspector);

        UserRole userRole = new UserRole(newInspector, inspectorRole);
        newInspector.getUserRoles().add(userRole);
        userRepository.save(newInspector);
    }

    @Override
    @Transactional(readOnly = true)
    public List<?> getStaffList(UUID managerId) {
        List<User> staffList;
        if (managerId != null) {
            staffList = userRepository.findByManagerId(managerId);
        } else {
            staffList = List.of();
        }
        return staffList.stream()
                .filter(u -> u.getUserRoles().stream()
                        .anyMatch(ur -> "INSPECTOR".equals(ur.getRole().getCode())))
                .map(userMapper::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void processExpiredApprovals() {
        List<InspectionOrder> expiredOrders = inspectionOrderRepository.findExpiredApprovalOrders(Instant.now());
        for (InspectionOrder order : expiredOrders) {
            order.setStatus(InspectionOrderStatus.CANCELLED.name());
            order.setApprovalDeadline(null);
            order.setNote("Tự động hủy do hết thời hạn phản hồi đề xuất kiểm định (24 giờ)");
            restorePostStatusAfterInspectionEnd(order);
            inspectionOrderRepository.save(order);
            log.info("Expired approval for inspection order {}", order.getId());
        }
    }

    private void assignActorForInspectionWork(InspectionOrder order, UUID actorId) {
        User actor = requireInspectionWorkActor(order, actorId);
        if (actor != null) order.setInspector(actor);
    }

    private User requireInspectionWorkActor(InspectionOrder order, UUID actorId) {
        if (order.getInspector() != null && actorId.equals(order.getInspector().getId())) return null;
        User actor = userRepository.findById(actorId).orElseThrow(
                () -> new ForbiddenException("Bạn không phải kỹ thuật viên được phân công cho đơn kiểm định này"));
        if (actor.hasRole("STAFF") || actor.hasRole("ADMIN")) return actor;
        User center = order.getInspectionCenter();
        if (center == null && order.getInspector() != null) center = order.getInspector().getManager();
        if (actor.hasRole("INSPECTOR") && (center == null
                || (actor.getManager() != null && center.getId().equals(actor.getManager().getId())))) {
            return actor;
        }
        throw new ForbiddenException("Đơn kiểm định không thuộc phạm vi của kỹ thuật viên");
    }

    private void requireAssignedHomeInspector(InspectionOrder order, UUID actorId) {
        if (order.getInspector() == null || !actorId.equals(order.getInspector().getId())) {
            throw new ForbiddenException("Chỉ kỹ thuật viên được phân công mới có thể kiểm định tại nhà");
        }
    }

    private void storeInspectionPhotos(InspectionOrder order, List<String> photos) {
        if (photos == null || photos.isEmpty()) {
            if (order.getOrderType() == InspectionOrderType.AT_CENTER) {
                throw new BadRequestException("Cần ít nhất một ảnh bằng chứng đã tải lên để nộp biên bản tại trung tâm");
            }
            return;
        }
        if (photos.size() > 10 || photos.stream().anyMatch(url -> url == null || url.length() > 2048
                || !evidencePhotoUrl.matcher(url).matches())) {
            throw new BadRequestException("Chỉ chấp nhận tối đa 10 URL ảnh từ thư mục ảnh kiểm định đã tải lên");
        }
        order.setEvidencePhotoUrls(photos.stream().distinct().toList());
    }

    private void requireFundedConfiguredRequest(InspectionOrder order) {
        if (order.getChecklistSnapshotJson() == null || order.getInspectionScope() != InspectionScope.BUYER_REQUEST) return;
        requireInspectionFeesPaid(order);
    }

    private void setFeeShares(InspectionOrder order, BigDecimal totalFee) {
        BigDecimal outboundCash = order.isCashShippingSimulation() ? order.getShippingFee() : BigDecimal.ZERO;
        BigDecimal remainingFees = totalFee.subtract(outboundCash);
        BigDecimal buyerRemaining = order.getFeePayer().buyerShare(remainingFees);
        order.setBuyerFeeShare(buyerRemaining.add(order.getFeePayer().buyerShare(outboundCash)));
        order.setSellerFeeShare(remainingFees.subtract(buyerRemaining));
    }

    private void requireInspectionFeesPaid(InspectionOrder order) {
        BigDecimal buyerFee = order.getBuyerFeeShare() == null ? BigDecimal.ZERO : order.getBuyerFeeShare();
        BigDecimal sellerFee = order.getSellerFeeShare() == null ? BigDecimal.ZERO : order.getSellerFeeShare();
        BigDecimal deposit = order.getBuyerCommitmentDeposit() == null ? BigDecimal.ZERO : order.getBuyerCommitmentDeposit();
        if ((buyerFee.add(deposit).signum() > 0 && order.getBuyerPaidAt() == null)
                || (sellerFee.signum() > 0 && order.getSellerPaidAt() == null)) {
            throw new ConflictException("Cần hoàn tất thanh toán phí trước khi thực hiện kiểm định");
        }
    }

    @Override
    @Transactional
    public void processExpiredFinalPayment(UUID orderId) {
        lockPostForFinalDecision(orderId);
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));
        Instant deadline = order.getFinalPaymentDeadline();
        if (!InspectionOrderStatus.PASSED.name().equals(order.getStatus())
                || order.getFinalPaymentPaidAt() != null || deadline == null || !deadline.isBefore(Instant.now())) {
            return;
        }

        String reason = "Người mua không thanh toán tiền hàng trong vòng 6 giờ sau khi kiểm định đạt chuẩn.";
        order.setStatus(InspectionOrderStatus.CANCELLED.name());
        order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + reason);
        feeSettlementService.settle(order, InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED, reason);
        restorePostStatusAfterInspectionEnd(order);
        inspectionOrderRepository.save(order);
        log.info("Expired final payment for inspection order {}", orderId);

        if (notificationService != null) {
            if (order.getSeller() != null) {
                notificationService.notify(order.getSeller().getId(), "Đơn kiểm định đã hủy do quá hạn thanh toán",
                        reason, NotificationType.ORDER, orderId, "INSPECTION_ORDER", "/seller/inspections/" + orderId);
            }
            if (order.getBuyer() != null) {
                notificationService.notify(order.getBuyer().getId(), "Đơn kiểm định đã bị hủy do quá hạn thanh toán",
                        reason, NotificationType.ORDER, orderId, "INSPECTION_ORDER", "/buyer/inspections/" + orderId);
            }
        }
    }

    private Post lockPostForFinalDecision(UUID orderId) {
        UUID postId = inspectionOrderRepository.findPostId(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));
        return postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException("Bài đăng không tồn tại"));
    }

    private void restorePostStatusAfterInspectionEnd(InspectionOrder order) {
        Post post = order.getPost();
        if (post != null && "RESERVED".equals(post.getStatus())) {
            boolean hasActiveNegotiation = order.getNegotiation() != null
                    && order.getNegotiation().getStatus() == NegotiationStatus.ACCEPTED
                    && order.getNegotiation().getExpiredAt() != null
                    && order.getNegotiation().getExpiredAt().isAfter(Instant.now());
            post.setStatus(hasActiveNegotiation ? "RESERVED" : "ACTIVE");
            postRepository.save(post);
        }
    }

    private void notifyCenterManagersPendingDispatch(InspectionOrder order) {
        if (notificationService == null || userRepository == null)
            return;
        String postTitle = order.getPost() != null && order.getPost().getTitle() != null ? order.getPost().getTitle()
                : "";
        List<UUID> centerManagerIds = userRepository.findActiveInspectionCenterUserIds();
        for (UUID centerId : centerManagerIds) {
            notificationService.notify(
                    centerId,
                    "Đơn kiểm định mới cần phân công KTV",
                    "Đơn kiểm định cho sản phẩm \"" + postTitle + "\" đã sẵn sàng để phân công Kỹ thuật viên.",
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/inspector-center/orders");
        }
    }

    private ShippingAddressRequest parseStoredBuyerAddress(String json) {
        if (json == null || json.isBlank())
            return null;
        try {
            return objectMapper.readValue(json, ShippingAddressRequest.class);
        } catch (Exception ex) {
            log.warn("Failed to parse stored buyer address JSON: {}", ex.getMessage());
            return null;
        }
    }

    private String aiScanPost(Post post) {
        String prompt = String.format("""
                Bạn là hệ thống kiểm duyệt tự động cho sàn mua bán đồ cũ SecondLife.
                Hãy đánh giá bài đăng sản phẩm sau có phù hợp để hiển thị trên sàn hay không:
                Tiêu đề: %s
                Mô tả: %s
                Giá: %s VNĐ

                TIÊU CHÍ KIỂM DUYỆT:
                1. HỢP LỆ (APPROVED):
                   - Sản phẩm là đồ dùng, gia dụng, thiết bị sinh hoạt cũ/đã qua sử dụng hợp pháp.
                   - Mô tả rõ ràng (chấp nhận định dạng Markdown hoặc mô tả do AI hỗ trợ tạo).
                   - Mức giá khả thi (không phải giá 0 VNĐ hoặc spam số ảo).
                2. TỪ CHỐI (REJECTED):
                   - Hàng cấm theo quy định (vũ khí, ma túy, chất kích thích, động vật quý hiếm, nội dung đồi trụy).
                   - Dấu hiệu lừa đảo trắng trợn, spam vô nghĩa, lôi kéo giao dịch ngoài sàn.
                   - Tiêu đề và mô tả hoàn toàn sai lệch hoặc không liên quan đến đồ dùng cần bán.

                QUY TẮC BẮT BUỘC:
                CHỈ trả về đúng một trong hai dạng sau trên một dòng duy nhất, không giải thích thêm:
                APPROVED
                hoặc
                REJECTED:<lý do ngắn gọn bằng tiếng Việt>
                """,
                post.getTitle(), post.getDescription(), post.getPrice());

        Prompt aiPrompt = new Prompt(new UserMessage(prompt));
        String result;
        try {
            result = googleChatGateway.complete(aiPrompt);
        } catch (Exception ex) {
            throw new AiProviderException("AI moderation unavailable; no listing credit was consumed", ex);
        }
        if (result == null)
            throw new AiProviderException("AI moderation returned no result");
        result = result.trim();
        if (!"APPROVED".equals(result) && !(result.startsWith("REJECTED:") && !result.substring(9).trim().isEmpty())) {
            throw new AiProviderException("AI moderation returned an invalid result");
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionSealVerificationResponse getSealVerification(String tamperSealId) {
        if (tamperSealId == null || tamperSealId.isBlank()) {
            throw new BadRequestException("Mã tem niêm phong không được để trống");
        }
        InspectionOrder order = inspectionOrderRepository.findByTamperSealId(tamperSealId.trim())
                .orElseThrow(
                        () -> new NotFoundException("Không tìm thấy tem niêm phong hợp lệ với mã: " + tamperSealId));

        List<InspectionSealLog> logs = inspectionSealLogRepository != null
                ? inspectionSealLogRepository.findByInspectionOrderIdOrderByScannedAtAsc(order.getId())
                : List.of();

        String currentStatus = "INTACT";
        for (InspectionSealLog l : logs) {
            if ("TAMPERED".equalsIgnoreCase(l.getSealCondition())) {
                currentStatus = "TAMPERED";
                break;
            }
        }

        List<InspectionSealLogResponse> logResponses = logs.stream()
                .map(inspectionSealLogMapper::toResponse)
                .toList();

        Post post = order.getPost();
        String inspectorName = "KTV SecondLife";
        if (order.getInspector() != null) {
            if (order.getInspector().getProfile() != null && order.getInspector().getProfile().getFullName() != null) {
                inspectorName = order.getInspector().getProfile().getFullName();
            } else {
                inspectorName = order.getInspector().getEmail();
            }
        }

        String productImageUrl = null;
        if (post != null) {
            if (post.getImageUrls() != null && !post.getImageUrls().isEmpty()) {
                productImageUrl = post.getImageUrls().get(0);
            } else {
                productImageUrl = post.getImageUrl();
            }
        }

        return InspectionSealVerificationResponse.builder()
                .tamperSealId(tamperSealId.trim())
                .inspectionOrderId(order.getId())
                .postId(post != null ? post.getId() : null)
                .productTitle(post != null ? post.getTitle() : null)
                .productImageUrl(productImageUrl)
                .productModel(null)
                .productBrand(null)
                .conditionScore(order.getConditionScore())
                .conditionGrade(order.getConditionGrade())
                .inspectionStatus(order.getStatus())
                .inspectionCenterName("Trung tâm kiểm định SecondLife")
                .inspectorName(inspectorName)
                .inspectedAt(order.getUpdatedAt())
                .currentSealStatus(currentStatus)
                .isOfficialSecondLifeSeal(true)
                .scanHistory(logResponses)
                .build();
    }

    @Override
    @Transactional
    public InspectionSealLogResponse recordSealScan(UUID actorId, InspectionSealScanRequest request) {
        if (request.tamperSealId() == null || request.tamperSealId().isBlank()) {
            throw new BadRequestException("Mã tem niêm phong không được để trống");
        }

        InspectionOrder order = inspectionOrderRepository.findByTamperSealId(request.tamperSealId().trim())
                .orElseThrow(() -> new NotFoundException(
                        "Không tìm thấy thông tin tem niêm phong: " + request.tamperSealId()));

        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;

        InspectionSealLog sealLog = new InspectionSealLog(
                order,
                request.tamperSealId().trim(),
                actor,
                request.scanStage().trim().toUpperCase(),
                request.sealCondition().trim().toUpperCase(),
                request.locationText(),
                request.latitude(),
                request.longitude(),
                request.note());

        sealLog = inspectionSealLogRepository.save(sealLog);

        if (notificationService != null && "TAMPERED".equalsIgnoreCase(request.sealCondition())) {
            if (order.getBuyer() != null) {
                notificationService.notify(
                        order.getBuyer().getId(),
                        "Cảnh báo tem niêm phong bị rách/bóc!",
                        "Tem niêm phong " + request.tamperSealId() + " được ghi nhận có dấu hiệu bị bóc ở giai đoạn "
                                + request.scanStage() + ".",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION",
                        "/orders/" + order.getId());
            }
        }

        return inspectionSealLogMapper.toResponse(sealLog);
    }

    @Override
    @Transactional
    public String generateSealForOrder(UUID inspectorId, UUID orderId) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));
        if (order.getInspector() != null && !order.getInspector().getId().equals(inspectorId)) {
            throw new ForbiddenException("Bạn không được phân công kiểm định cho đơn này");
        }
        if (order.getTamperSealId() != null && !order.getTamperSealId().isBlank()) {
            return order.getTamperSealId();
        }
        if (order.getOrderType() == InspectionOrderType.AT_CENTER) {
            throw new ConflictException("Biên bản kiểm định tại trung tâm sử dụng ảnh bằng chứng");
        }
        requireEditableInspectionOrder(order);
        String newSeal = tamperSealService.generateSealCode();
        order.setTamperSealId(newSeal);
        inspectionOrderRepository.save(order);
        return newSeal;
    }

    @Override
    @Transactional
    public InspectionOrderResponse submitAppeal(UUID sellerId, UUID orderId, InspectionAppealRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getSeller() == null || !order.getSeller().getId().equals(sellerId)) {
            throw new ForbiddenException("Bạn không phải người bán của đơn kiểm định này");
        }

        if (!InspectionOrderStatus.FAILED.name().equals(order.getStatus())) {
            throw new ConflictException("Chỉ đơn kiểm định có kết quả KHÔNG ĐẠT (FAILED) mới có thể khiếu nại");
        }

        if (order.getAppealStatus() != null && order.getAppealStatus() != InspectionAppealStatus.NONE) {
            throw new ConflictException("Đơn kiểm định này đã được gửi khiếu nại trước đó");
        }

        if (order.getAppealDeadline() != null && order.getAppealDeadline().isBefore(Instant.now())) {
            throw new ConflictException("Đã hết thời hạn 24 giờ để khiếu nại kết quả kiểm định");
        }

        order.setAppealStatus(InspectionAppealStatus.PENDING_REVIEW);
        order.setAppealReason(request.reason());
        try {
            order.setAppealEvidenceJson(objectMapper.writeValueAsString(request));
        } catch (Exception ex) {
            log.warn("Failed to serialize appeal evidence JSON: {}", ex.getMessage());
            order.setAppealEvidenceJson(request.reason());
        }

        InspectionOrder saved = inspectionOrderRepository.save(order);
        log.info("Seller {} submitted appeal for failed inspection order {}", sellerId, orderId);

        if (notificationService != null && userRepository != null) {
            List<UUID> centerManagerIds = userRepository.findActiveInspectionCenterUserIds();
            for (UUID centerId : centerManagerIds) {
                notificationService.notify(
                        centerId,
                        "Khiếu nại kết quả kiểm định mới",
                        "Người bán đã gửi khiếu nại kết quả kiểm định cho sản phẩm \"" + (order.getPost() != null ? order.getPost().getTitle() : "") + "\". Vui lòng xem xét.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/inspector-center/orders/" + order.getId()
                );
            }
        }

        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionOrderResponse acceptFailedResult(UUID sellerId, UUID orderId) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getSeller() == null || !order.getSeller().getId().equals(sellerId)) {
            throw new ForbiddenException("Bạn không phải người bán của đơn kiểm định này");
        }

        if (!InspectionOrderStatus.FAILED.name().equals(order.getStatus())) {
            throw new ConflictException("Chỉ đơn kiểm định có kết quả KHÔNG ĐẠT (FAILED) mới có thể xác nhận");
        }

        if (order.isReputationPenaltyApplied()) {
            return inspectionOrderMapper.toResponse(order);
        }

        boolean[] seriousFlag = new boolean[]{false};
        BigDecimal points = calculateChecklistPenalty(order, seriousFlag);
        order.setAppealDeadline(null);
        order.setReputationPenaltyApplied(true);
        order.setReputationPenaltyPoints(points);

        sellerReputationService.recordInspectionFailurePenalty(
                sellerId,
                order.getId(),
                points,
                "Người bán chấp nhận kết quả kiểm định không đạt (-" + points.toPlainString() + " điểm)",
                seriousFlag[0]
        );

        InspectionOrder saved = inspectionOrderRepository.save(order);
        log.info("Seller {} voluntarily accepted failed inspection order {}. Deducted {} points", sellerId, orderId, points);

        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionOrderResponse reviewAppeal(UUID reviewerId, UUID orderId, ReviewInspectionAppealRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new NotFoundException("Tài khoản người duyệt không tồn tại"));

        boolean isPlatformReviewer = reviewer.hasRole("ADMIN") || reviewer.hasRole("STAFF");
        boolean ownsCenterOrder = order.getInspectionCenter() != null && reviewerId.equals(order.getInspectionCenter().getId());
        boolean managesAssignedInspector = order.getInspector() != null && order.getInspector().getManager() != null
                && reviewerId.equals(order.getInspector().getManager().getId());
        if (!isPlatformReviewer && !(reviewer.hasRole("INSPECTION_CENTER") && (ownsCenterOrder || managesAssignedInspector))) {
            throw new ForbiddenException("Bạn không có quyền thẩm duyệt khiếu nại kiểm định");
        }

        if (order.getAppealStatus() != InspectionAppealStatus.PENDING_REVIEW) {
            throw new ConflictException("Đơn kiểm định không ở trạng thái chờ duyệt khiếu nại");
        }

        order.setAppealResolvedAt(Instant.now());
        order.setAppealResolvedBy(reviewer);

        if ("UPHELD".equalsIgnoreCase(request.decision())) {
            order.setAppealStatus(InspectionAppealStatus.UPHELD);
            boolean[] seriousFlag = new boolean[]{false};
            BigDecimal points = calculateChecklistPenalty(order, seriousFlag);
            order.setReputationPenaltyApplied(true);
            order.setReputationPenaltyPoints(points);

            if (order.getSeller() != null) {
                sellerReputationService.recordInspectionFailurePenalty(
                        order.getSeller().getId(),
                        order.getId(),
                        points,
                        "Bác khiếu nại kiểm định: KTV kết luận chính xác sản phẩm có sai lệch (-" + points.toPlainString() + " điểm)",
                        seriousFlag[0]
                );
                if (notificationService != null) {
                    notificationService.notify(
                            order.getSeller().getId(),
                            "Khiếu nại kiểm định đã bị bác bỏ",
                            "Quản lý trung tâm đã xem xét và bác khiếu nại kiểm định của bạn: " + (request.resolutionNote() != null ? request.resolutionNote() : "Kết luận của KTV là chính xác.") + " Bạn bị trừ " + points + " điểm uy tín.",
                            NotificationType.INSPECTION,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/seller/inspections/" + order.getId()
                    );
                }
            }
        } else if ("OVERTURNED".equalsIgnoreCase(request.decision())) {
            order.setAppealStatus(InspectionAppealStatus.OVERTURNED);
            order.setReputationPenaltyApplied(false);
            order.setReputationPenaltyPoints(BigDecimal.ZERO);

            if (notificationService != null && order.getSeller() != null) {
                notificationService.notify(
                        order.getSeller().getId(),
                        "Khiếu nại kiểm định đã được chấp thuận",
                        "Quản lý trung tâm đã chấp thuận khiếu nại của bạn: " + (request.resolutionNote() != null ? request.resolutionNote() : "Điểm uy tín của bạn được giữ nguyên."),
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId()
                );
            }
        }

        InspectionOrder saved = inspectionOrderRepository.save(order);
        log.info("Reviewer {} resolved appeal for order {} with decision {}", reviewerId, orderId, request.decision());
        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void processExpiredAppeals() {
        List<InspectionOrder> expiredOrders = inspectionOrderRepository.findExpiredAppeals(Instant.now());
        for (InspectionOrder order : expiredOrders) {
            try {
                if (order.getSeller() == null || order.isReputationPenaltyApplied()) continue;
                boolean[] seriousFlag = new boolean[]{false};
                BigDecimal points = calculateChecklistPenalty(order, seriousFlag);
                order.setReputationPenaltyApplied(true);
                order.setReputationPenaltyPoints(points);

                sellerReputationService.recordInspectionFailurePenalty(
                        order.getSeller().getId(),
                        order.getId(),
                        points,
                        "Hết hạn 24 giờ khiếu nại kiểm định: Chấp nhận kết quả sản phẩm sai lệch (-" + points.toPlainString() + " điểm)",
                        seriousFlag[0]
                );

                inspectionOrderRepository.save(order);
                log.info("Expired 24h appeal window for inspection order {}. Deducted {} reputation points from seller {}",
                        order.getId(), points, order.getSeller().getId());

                if (notificationService != null) {
                    notificationService.notify(
                            order.getSeller().getId(),
                            "Hết hạn khiếu nại kiểm định",
                            "Đã hết thời hạn 24 giờ khiếu nại kết quả kiểm định. Hệ thống đã trừ " + points + " điểm uy tín của bạn do sản phẩm không đạt chuẩn mô tả.",
                            NotificationType.INSPECTION,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/seller/inspections/" + order.getId()
                    );
                }
            } catch (Exception ex) {
                log.error("Error processing expired appeal for order {}: {}", order.getId(), ex.getMessage());
            }
        }
    }

    private BigDecimal calculateChecklistPenalty(InspectionOrder order, boolean[] outSerious) {
        if (order == null) return new BigDecimal("15.00");

        Integer score = order.getConditionScore();
        boolean isPoor = "POOR".equalsIgnoreCase(order.getConditionGrade()) || (score != null && score < 40);
        if (isPoor) {
            if (outSerious != null && outSerious.length > 0) outSerious[0] = true;
            return new BigDecimal("40.00");
        }

        String json = order.getChecklistResultJson();
        if (json == null || json.isBlank()) {
            return new BigDecimal("15.00");
        }

        try {
            List<ChecklistItemResultDto> items = objectMapper.readValue(
                    json,
                    new TypeReference<List<ChecklistItemResultDto>>() {}
            );
            if (items == null || items.isEmpty()) {
                return new BigDecimal("15.00");
            }

            long total = items.size();
            long failed = items.stream().filter(it -> !it.passed()).count();

            double failureRate = (double) failed / total;

            if (failureRate > 0.6) {
                if (outSerious != null && outSerious.length > 0) outSerious[0] = true;
                return new BigDecimal("40.00");
            } else if (failureRate > 0.3) {
                return new BigDecimal("20.00");
            } else {
                return new BigDecimal("10.00");
            }
        } catch (Exception ex) {
            log.warn("Could not parse checklistResultJson for penalty calculation: {}", ex.getMessage());
            return new BigDecimal("15.00");
        }
    }
}

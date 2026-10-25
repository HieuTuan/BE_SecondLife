package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionAppointmentRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionRejectPurchaseRequest;
import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.request.InspectionSealScanRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.dto.response.InspectionSealLogResponse;
import com.secondlife.secondlife.dto.response.InspectionSealVerificationResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.InspectionSealLog;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.entity.UserRole;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.exception.AiProviderException;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.mapper.InspectionSealLogMapper;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.InspectionSealLogRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;
import com.secondlife.secondlife.service.ListingPublicationService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.TamperSealService;
import com.secondlife.secondlife.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InspectionServiceImpl implements InspectionService {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final PostRepository postRepository;
    private final ShipmentRepository shipments;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final ListingPublicationService publication;
    private final InspectionPricingService inspectionPricingService;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final InspectionSealLogRepository inspectionSealLogRepository;
    private final TamperSealService tamperSealService;

    @Qualifier("googleChatGateway")
    private final AiChatGateway googleChatGateway;
    private final InspectionOrderMapper inspectionOrderMapper;
    private final InspectionSealLogMapper inspectionSealLogMapper;

    public InspectionServiceImpl(InspectionOrderRepository inspectionOrderRepository,
                                PostRepository postRepository,
                                ShipmentRepository shipments,
                                UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder,
                                UserMapper userMapper,
                                ListingPublicationService publication,
                                InspectionPricingService inspectionPricingService,
                                WalletService walletService,
                                ObjectMapper objectMapper,
                                NotificationService notificationService,
                                InspectionSealLogRepository inspectionSealLogRepository,
                                TamperSealService tamperSealService,
                                @Qualifier("googleChatGateway") AiChatGateway googleChatGateway,
                                InspectionOrderMapper inspectionOrderMapper,
                                InspectionSealLogMapper inspectionSealLogMapper) {
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.postRepository = postRepository;
        this.shipments = shipments;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.publication = publication;
        this.inspectionPricingService = inspectionPricingService;
        this.walletService = walletService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.inspectionSealLogRepository = inspectionSealLogRepository;
        this.tamperSealService = tamperSealService;
        this.googleChatGateway = googleChatGateway;
        this.inspectionOrderMapper = inspectionOrderMapper;
        this.inspectionSealLogMapper = inspectionSealLogMapper;
    }

    @Override
    @Transactional
    public InspectionOrderResponse requestInspection(UUID buyerId, InspectionBuyRequest request) {
        Post post = postRepository.findByIdForUpdate(request.postId())
                .orElseThrow(() -> new NotFoundException("Bài đăng không tồn tại"));

        if (post.getUser() != null && post.getUser().getId().equals(buyerId)) {
            throw new BadRequestException("Bạn không thể tạo yêu cầu kiểm định cho bài đăng của chính mình");
        }

        if (!"ACTIVE".equals(post.getStatus())) {
            throw new BadRequestException("Bài đăng hiện không ở trạng thái sẵn sàng để giao dịch");
        }

        // Kiểm tra xem bài đăng đã có đơn kiểm định đang xử lý không
        List<InspectionOrder> existingOrders = inspectionOrderRepository.findByPostId(post.getId()).stream().toList();
        boolean hasActiveInspection = existingOrders.stream()
                .anyMatch(o -> !List.of("CANCELLED", "FAILED", "PAYMENT_EXPIRED", "COMPLETED").contains(o.getStatus()));
        if (hasActiveInspection) {
            throw new ConflictException("Bài đăng đã có đơn kiểm định khác đang diễn ra");
        }

        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin người mua"));
        User seller = post.getUser();

        // Tính toán báo giá chi tiết cho 2 gói
        InspectionQuoteResponse quote = inspectionPricingService.calculateQuote(
                InspectionQuoteRequest.builder()
                        .postId(post.getId())
                        .buyerLatitude(request.buyerLatitude())
                        .buyerLongitude(request.buyerLongitude())
                        .build()
        );

        InspectionOrderType orderType = request.orderType() != null ? request.orderType() : InspectionOrderType.AT_CENTER;
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

        BigDecimal buyerFeeShare;
        BigDecimal sellerFeeShare;

        switch (feePayer) {
            case BUYER_PAYS -> {
                buyerFeeShare = totalFee;
                sellerFeeShare = BigDecimal.ZERO;
            }
            case SELLER_PAYS -> {
                buyerFeeShare = BigDecimal.ZERO;
                sellerFeeShare = totalFee;
            }
            case SPLIT_50_50 -> {
                buyerFeeShare = totalFee.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                sellerFeeShare = totalFee.subtract(buyerFeeShare);
            }
            default -> throw new BadRequestException("Tùy chọn phân chia phí không hợp lệ");
        }

        InspectionOrder order = new InspectionOrder();
        order.setPost(post);
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setOrderType(orderType);
        order.setFeePayer(feePayer);
        order.setStatus(InspectionOrderStatus.WAITING_SELLER_APPROVAL.name());

        order.setInspectionFee(inspectionFee);
        order.setShippingFee(shippingFee);
        order.setTravelDistanceKm(travelDist);
        order.setTravelFee(travelFee);
        order.setReturnShippingReserveFee(returnReserve);

        order.setBuyerFeeShare(buyerFeeShare);
        order.setSellerFeeShare(sellerFeeShare);

        order.setBuyerConsentZin(request.buyerConsentZinLoss());
        order.setSellerConsentZin(false);

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
                "/buyer/inspections/" + saved.getId()
            );
            if (seller != null) {
                notificationService.notify(
                    seller.getId(),
                    "Yêu cầu kiểm định mới",
                    "Có người mua yêu cầu kiểm định cho sản phẩm \"" + post.getTitle() + "\". Vui lòng kiểm tra và phản hồi.",
                    NotificationType.INSPECTION,
                    saved.getId(),
                    "INSPECTION_ORDER",
                    "/seller/inspections/" + saved.getId()
                );
            }
        }

        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionOrderResponse respondInspection(UUID sellerId, UUID orderId, InspectionSellerRespondRequest request) {
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
            order.setNote("Người bán từ chối: " + (request.rejectReason() != null ? request.rejectReason() : "Không nêu lý do"));
            // Mở lại bài đăng về ACTIVE
            Post post = order.getPost();
            if (post != null && "RESERVED".equals(post.getStatus())) {
                post.setStatus("ACTIVE");
                postRepository.save(post);
            }
            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getBuyer() != null) {
                notificationService.notify(
                    order.getBuyer().getId(),
                    "Người bán từ chối kiểm định",
                    "Người bán đã từ chối yêu cầu kiểm định cho sản phẩm \"" + (post != null ? post.getTitle() : "") + "\". Lý do: " + (request.rejectReason() != null ? request.rejectReason() : "Không nêu lý do"),
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/buyer/inspections/" + order.getId()
                );
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

            switch (request.counterFeePayer()) {
                case BUYER_PAYS -> {
                    order.setBuyerFeeShare(totalFee);
                    order.setSellerFeeShare(BigDecimal.ZERO);
                }
                case SELLER_PAYS -> {
                    order.setBuyerFeeShare(BigDecimal.ZERO);
                    order.setSellerFeeShare(totalFee);
                }
                case SPLIT_50_50 -> {
                    BigDecimal half = totalFee.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    order.setBuyerFeeShare(half);
                    order.setSellerFeeShare(totalFee.subtract(half));
                }
            }
            order.setStatus(InspectionOrderStatus.WAITING_BUYER_APPROVAL.name());
            order.setNote("Người bán đề xuất lại phương án chia phí: " + request.counterFeePayer());
            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getBuyer() != null) {
                notificationService.notify(
                    order.getBuyer().getId(),
                    "Người bán đề xuất lại phí kiểm định",
                    "Người bán đã đề xuất lại phương án chia phí kiểm định cho sản phẩm \"" + (order.getPost() != null ? order.getPost().getTitle() : "") + "\".",
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/buyer/inspections/" + order.getId()
                );
            }
            return inspectionOrderMapper.toResponse(saved);
        }

        if ("ACCEPT".equals(action)) {
            if (!request.sellerConsentZinLoss()) {
                throw new BadRequestException("Người bán bắt buộc phải xác nhận đồng ý tháo máy kiểm định (nguy cơ mất zin)");
            }
            order.setSellerConsentZin(true);
            order.setStatus(InspectionOrderStatus.WAITING_FEE_PAYMENT.name());
            order.setPaymentDeadline(Instant.now().plus(Duration.ofHours(6)));

            // Nếu bên nào có phí = 0 thì coi như đã thanh toán
            if (order.getBuyerFeeShare() == null || order.getBuyerFeeShare().compareTo(BigDecimal.ZERO) == 0) {
                order.setBuyerPaidAt(Instant.now());
            }
            if (order.getSellerFeeShare() == null || order.getSellerFeeShare().compareTo(BigDecimal.ZERO) == 0) {
                order.setSellerPaidAt(Instant.now());
            }

            // Nếu cả hai đã hoàn tất thanh toán
            if (order.getBuyerPaidAt() != null && order.getSellerPaidAt() != null) {
                order.setStatus(InspectionOrderStatus.CONFIRMED_PENDING_DISPATCH.name());
            }

            InspectionOrder saved = inspectionOrderRepository.save(order);
            if (notificationService != null && order.getBuyer() != null) {
                notificationService.notify(
                    order.getBuyer().getId(),
                    "Người bán đồng ý kiểm định",
                    "Người bán đã đồng ý kiểm định cho sản phẩm \"" + (order.getPost() != null ? order.getPost().getTitle() : "") + "\". Vui lòng hoàn tất thanh toán phí kiểm định.",
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/buyer/inspections/" + order.getId()
                );
            }
            if (notificationService != null && userRepository != null) {
                String postTitle = order.getPost() != null && order.getPost().getTitle() != null ? order.getPost().getTitle() : "";
                List<UUID> centerManagerIds = userRepository.findActiveInspectionCenterUserIds();
                if (InspectionOrderStatus.CONFIRMED_PENDING_DISPATCH.name().equals(order.getStatus())) {
                    for (UUID centerId : centerManagerIds) {
                        notificationService.notify(
                            centerId,
                            "Đơn kiểm định mới cần phân công KTV",
                            "Đơn kiểm định cho sản phẩm \"" + postTitle + "\" đã sẵn sàng phân công Kỹ thuật viên.",
                            NotificationType.INSPECTION,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/inspector-center/orders"
                        );
                    }
                } else if (InspectionOrderStatus.WAITING_FEE_PAYMENT.name().equals(order.getStatus())) {
                    for (UUID centerId : centerManagerIds) {
                        notificationService.notify(
                            centerId,
                            "Đơn kiểm định mới đang chờ nộp phí",
                            "Người bán đã đồng ý kiểm định cho sản phẩm \"" + postTitle + "\". Đơn đang chờ thanh toán phí kiểm định vào ví ký quỹ.",
                            NotificationType.INSPECTION,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/inspector-center/orders"
                        );
                    }
                }
            }
            return inspectionOrderMapper.toResponse(saved);
        }

        throw new BadRequestException("Hành động phản hồi phải là ACCEPT, REJECT hoặc COUNTER");
    }

    @Override
    @Transactional
    public InspectionOrderResponse scheduleAppointment(UUID actorId, UUID orderId, InspectionAppointmentRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        boolean isSeller = order.getSeller() != null && order.getSeller().getId().equals(actorId);
        boolean isInspector = order.getInspector() != null && order.getInspector().getId().equals(actorId);

        if (!isSeller && !isInspector) {
            throw new ForbiddenException("Chỉ Kỹ thuật viên phụ trách hoặc Người bán mới có thể chốt lịch hẹn");
        }

        if (order.getOrderType() != InspectionOrderType.AT_HOME) {
            throw new BadRequestException("Lên lịch hẹn chỉ áp dụng cho gói kiểm định tại nhà (AT_HOME)");
        }

        if (request.appointmentTime() == null || request.appointmentTime().isBefore(Instant.now())) {
            throw new BadRequestException("Thời gian hẹn phải nằm ở tương lai");
        }

        order.setAppointmentScheduledAt(request.appointmentTime());
        order.setStatus(InspectionOrderStatus.SCHEDULED.name());
        if (request.note() != null && !request.note().isBlank()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + "Lịch hẹn: " + request.note());
        }

        InspectionOrder saved = inspectionOrderRepository.save(order);
        if (notificationService != null) {
            String title = order.getPost() != null ? order.getPost().getTitle() : "";
            if (order.getBuyer() != null) {
                notificationService.notify(
                    order.getBuyer().getId(),
                    "Lịch hẹn kiểm định tại nhà",
                    "Đã ấn định lịch hẹn kiểm định tại nhà cho sản phẩm \"" + title + "\" vào lúc " + request.appointmentTime() + ".",
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/buyer/inspections/" + order.getId()
                );
            }
            if (order.getSeller() != null) {
                notificationService.notify(
                    order.getSeller().getId(),
                    "Lịch hẹn kiểm định tại nhà",
                    "Đã ấn định lịch hẹn KTV tới kiểm định sản phẩm \"" + title + "\" vào lúc " + request.appointmentTime() + ".",
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/seller/inspections/" + order.getId()
                );
            }
        }
        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public InspectionOrderResponse getOrderById(UUID actorId, UUID orderId) {
        InspectionOrder order = inspectionOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        User user = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin tài khoản"));

        boolean isParticipant = (order.getBuyer() != null && order.getBuyer().getId().equals(actorId))
                || (order.getSeller() != null && order.getSeller().getId().equals(actorId))
                || (order.getInspector() != null && order.getInspector().getId().equals(actorId));

        boolean isStaffOrAdmin = user.hasRole("INSPECTION_CENTER") || user.hasRole("ADMIN") || user.hasRole("STAFF");

        if (!isParticipant && !isStaffOrAdmin) {
            throw new ForbiddenException("Bạn không có quyền xem thông tin đơn kiểm định này");
        }

        return inspectionOrderMapper.toResponse(order);
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
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyerId)) {
            throw new ForbiddenException("Bạn không phải người mua của đơn kiểm định này");
        }

        if (!InspectionOrderStatus.PASSED.name().equals(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định phải ở trạng thái ĐẠT CHUẨN (PASSED) mới có thể xác nhận mua hàng");
        }

        if (order.getFinalPaymentDeadline() != null && order.getFinalPaymentDeadline().isBefore(Instant.now())) {
            throw new ConflictException("Thời hạn thanh toán tiền hàng (6 giờ) đã kết thúc");
        }

        if (order.getFinalPaymentPaidAt() != null) {
            throw new ConflictException("Đơn hàng đã được thanh toán tiền hàng rồi");
        }

        BigDecimal itemPrice = order.getFinalItemPrice() != null ? order.getFinalItemPrice() : (order.getPost() != null ? order.getPost().getPrice() : BigDecimal.ZERO);
        BigDecimal finalShipping = order.getFinalShippingFee() != null ? order.getFinalShippingFee() : BigDecimal.ZERO;
        BigDecimal totalPayment = itemPrice.add(finalShipping);

        if (totalPayment.compareTo(BigDecimal.ZERO) > 0) {
            walletService.processPayment(buyerId, totalPayment, order.getId(),
                    WalletTransactionReferenceType.INSPECTION_ORDER);
        }

        order.setFinalPaymentPaidAt(Instant.now());
        order.setStatus(InspectionOrderStatus.DELIVERING_TO_BUYER.name());

        Post post = order.getPost();
        if (post != null) {
            post.setStatus("SOLD");
            postRepository.save(post);
        }

        InspectionOrder savedOrder = inspectionOrderRepository.save(order);

        if (notificationService != null) {
            String title = post != null ? post.getTitle() : "";
            notificationService.notifyWithEmail(
                buyerId,
                "Thanh toán mua hàng thành công",
                "Bạn đã hoàn tất thanh toán mua sản phẩm \"" + title + "\" sau kiểm định. Đơn hàng đang được chuẩn bị giao.",
                NotificationType.ORDER,
                order.getId(),
                "INSPECTION_ORDER",
                "/buyer/orders/" + order.getId()
            );
            if (order.getSeller() != null) {
                notificationService.notifyWithEmail(
                    order.getSeller().getId(),
                    "Khách hàng đã thanh toán mua sản phẩm",
                    "Người mua đã hoàn tất thanh toán cho sản phẩm \"" + title + "\". Vui lòng chuẩn bị giao hàng.",
                    NotificationType.ORDER,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/seller/orders/" + order.getId()
                );
            }
        }

        return inspectionOrderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional
    public InspectionOrderResponse rejectFinalPurchase(UUID buyerId, UUID orderId, InspectionRejectPurchaseRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getBuyer() == null || !order.getBuyer().getId().equals(buyerId)) {
            throw new ForbiddenException("Bạn không phải người mua của đơn kiểm định này");
        }

        if (!InspectionOrderStatus.PASSED.name().equals(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định phải ở trạng thái ĐẠT CHUẨN (PASSED) mới có thể từ chối mua tiếp");
        }

        if (order.getFinalPaymentPaidAt() != null) {
            throw new ConflictException("Đơn hàng đã được thanh toán tiền hàng rồi, không thể từ chối");
        }

        String reason = (request != null && request.reason() != null && !request.reason().isBlank())
                ? request.reason().trim()
                : "Người mua từ chối mua tiếp sau khi có kết quả kiểm định đạt chuẩn";

        order.setStatus("CANCELLED");
        order.setNote((order.getNote() != null ? order.getNote() + " | " : "") +
                "Người mua từ chối mua tiếp sau kiểm định. Lý do: " + reason);

        BigDecimal buyerDeposit = (order.getBuyerPaidAt() != null && order.getBuyerFeeShare() != null)
                ? order.getBuyerFeeShare()
                : BigDecimal.ZERO;

        // Bồi hoàn tiền cọc kiểm định của người mua cho người bán theo cam kết
        if (order.getSeller() != null && buyerDeposit.compareTo(BigDecimal.ZERO) > 0) {
            try {
                walletService.processEarning(order.getSeller().getId(), buyerDeposit, order.getId(),
                        WalletTransactionReferenceType.INSPECTION_ORDER);
                log.info("Compensated buyer deposit fee share of {} to seller {} for rejected purchase on inspection order {}",
                        buyerDeposit, order.getSeller().getId(), order.getId());
            } catch (Exception ex) {
                log.error("Failed to compensate seller {} for rejected purchase order {}: {}",
                        order.getSeller().getId(), order.getId(), ex.getMessage());
            }
        }

        // Mở lại bài đăng sang trạng thái ACTIVE cho người bán
        Post post = order.getPost();
        if (post != null) {
            post.setStatus("ACTIVE");
            postRepository.save(post);
        }

        InspectionOrder savedOrder = inspectionOrderRepository.save(order);

        // Gửi thông báo & email cho Người bán và thông báo cho Người mua
        if (notificationService != null) {
            String title = post != null && post.getTitle() != null ? post.getTitle() : "";
            if (order.getSeller() != null) {
                String sellerMsg = "Người mua đã từ chối mua sản phẩm \"" + title + "\" sau kiểm định. Lý do: " + reason +
                        ". Tiền cọc kiểm định của người mua (" + buyerDeposit + " VND) đã được bồi hoàn vào ví của bạn. Bài đăng đã được mở lại sang ACTIVE.";
                notificationService.notifyWithEmail(
                    order.getSeller().getId(),
                    "Người mua từ chối mua tiếp sau kiểm định",
                    sellerMsg,
                    NotificationType.ORDER,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/seller/inspections/" + order.getId()
                );
            }

            notificationService.notify(
                buyerId,
                "Đã hủy đơn mua sau kiểm định",
                "Bạn đã từ chối mua tiếp sản phẩm \"" + title + "\". Đơn kiểm định đã kết thúc và tiền cọc kiểm định được bồi hoàn cho người bán theo chính sách cam kết.",
                NotificationType.ORDER,
                order.getId(),
                "INSPECTION_ORDER",
                "/buyer/inspections/" + order.getId()
            );
        }

        return inspectionOrderMapper.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionOrderResponse> getMyOrders(UUID inspectorId) {
        return inspectionOrderRepository.findByInspectorIdOrderByCreatedAtDesc(inspectorId)
                .stream()
                .map(inspectionOrderMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getAllOrders(String status, Pageable pageable) {
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            String normalized = status.trim().toUpperCase();
            if ("PENDING".equals(normalized)) {
                return inspectionOrderRepository.findByStatusIn(List.of("PENDING", "CONFIRMED_PENDING_DISPATCH"), pageable)
                        .map(inspectionOrderMapper::toResponse);
            }
            if ("ASSIGNED".equals(normalized)) {
                return inspectionOrderRepository.findByStatusIn(List.of("ASSIGNED", "DISPATCHED", "SCHEDULED"), pageable)
                        .map(inspectionOrderMapper::toResponse);
            }
            return inspectionOrderRepository.findByStatus(normalized, pageable)
                    .map(inspectionOrderMapper::toResponse);
        }
        return inspectionOrderRepository.findAll(pageable)
                .map(inspectionOrderMapper::toResponse);
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

        if (!"PENDING".equals(order.getStatus()) && !InspectionOrderStatus.CONFIRMED_PENDING_DISPATCH.name().equals(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định không ở trạng thái hợp lệ để phân công KTV");
        }

        User inspector = userRepository.findById(inspectorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin Kỹ thuật viên"));

        if (inspector.getAccountStatus() != AccountStatus.ACTIVE || !inspector.hasRole("INSPECTOR")) {
            throw new BadRequestException("Tài khoản được phân công phải là INSPECTOR đang hoạt động (ACTIVE)");
        }

        if (managerId != null && inspector.getManager() != null && !inspector.getManager().getId().equals(managerId)) {
            throw new ForbiddenException("Kỹ thuật viên không thuộc quyền quản lý của trung tâm bạn");
        }

        order.setInspector(inspector);
        order.setStatus(InspectionOrderStatus.DISPATCHED.name());
        InspectionOrder saved = inspectionOrderRepository.save(order);
        if (notificationService != null) {
            String title = order.getPost() != null ? order.getPost().getTitle() : "";
            notificationService.notify(
                inspector.getId(),
                "Phân công kiểm định mới",
                "Bạn được phân công kiểm định đơn cho sản phẩm \"" + title + "\".",
                NotificationType.INSPECTION,
                order.getId(),
                "INSPECTION_ORDER",
                "/inspector/inspections/" + order.getId()
            );
        }
        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionOrderResponse startInspection(UUID actorId, UUID orderId) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getInspector() == null || !order.getInspector().getId().equals(actorId)) {
            throw new ForbiddenException("Bạn không phải Kỹ thuật viên được phân công cho đơn kiểm định này");
        }

        List<String> allowedStartStatuses = List.of(
                "PENDING",
                "DISPATCHED",
                "SCHEDULED",
                "CONFIRMED_PENDING_DISPATCH",
                "SHIPPING_TO_CENTER",
                "INSPECTING"
        );
        if (!allowedStartStatuses.contains(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định không ở trạng thái hợp lệ để bắt đầu kiểm tra");
        }

        order.setStatus(InspectionOrderStatus.INSPECTING.name());
        InspectionOrder saved = inspectionOrderRepository.save(order);

        if (notificationService != null) {
            String title = order.getPost() != null ? order.getPost().getTitle() : "";
            if (order.getBuyer() != null) {
                notificationService.notify(
                    order.getBuyer().getId(),
                    "Kỹ thuật viên đã bắt đầu kiểm định",
                    "Sản phẩm \"" + title + "\" đang được kỹ thuật viên mở máy và kiểm tra tính năng.",
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/buyer/inspections/" + order.getId()
                );
            }
            if (order.getSeller() != null) {
                notificationService.notify(
                    order.getSeller().getId(),
                    "Kỹ thuật viên đã bắt đầu kiểm định",
                    "Sản phẩm \"" + title + "\" của bạn đang được kỹ thuật viên tiến hành kiểm tra.",
                    NotificationType.INSPECTION,
                    order.getId(),
                    "INSPECTION_ORDER",
                    "/seller/inspections/" + order.getId()
                );
            }
        }

        return inspectionOrderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InspectionOrderResponse submitResult(UUID orderId, UUID inspectorId, InspectionResultRequest request) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));

        if (order.getInspector() == null || !order.getInspector().getId().equals(inspectorId)) {
            throw new ForbiddenException("Đơn kiểm định này không được phân công cho bạn");
        }

        List<String> allowedStatuses = List.of("PENDING", "DISPATCHED", "SCHEDULED", "INSPECTING", "CONFIRMED_PENDING_DISPATCH");
        if (!allowedStatuses.contains(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định đã hoàn tất hoặc không ở trạng thái thực thi");
        }

        var carrierLegs = shipments.findByInspectionOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(s -> "SELLER_TO_CENTER".equals(s.getLeg()) && !"CANCELLED".equals(s.getStatus())).toList();
        if (!carrierLegs.isEmpty() && !"DELIVERED".equals(carrierLegs.getLast().getStatus())) {
            throw new ConflictException("Đơn vị vận chuyển phải xác nhận giao hàng tới Trung tâm kiểm định trước khi nộp kết quả");
        }

        order.setNote(request.note());
        order.setVideoProofUrl(request.videoProofUrl());
        order.setVideoProofPublicId(request.videoProofPublicId());
        order.setUnboxingVideoUrl(request.unboxingVideoUrl());
        order.setUnboxingVideoPublicId(request.unboxingVideoPublicId());

        boolean isExternalVideo = (request.videoProofUrl() != null && (request.videoProofUrl().contains("youtube.com") || request.videoProofUrl().contains("youtu.be") || request.videoProofUrl().contains("drive.google.com")))
                || (request.unboxingVideoUrl() != null && (request.unboxingVideoUrl().contains("youtube.com") || request.unboxingVideoUrl().contains("youtu.be") || request.unboxingVideoUrl().contains("drive.google.com")));

        if (isExternalVideo) {
            order.setVideoCleanupStatus("EXEMPTED");
        } else {
            order.setVideoCleanupStatus("ACTIVE");
        }

        if (request.conditionScore() != null) {
            order.setConditionScore(request.conditionScore());
        }
        if (request.conditionGrade() != null) {
            order.setConditionGrade(request.conditionGrade());
        }
        String sealId = null;
        if (request.tamperSealId() != null && !request.tamperSealId().isBlank()) {
            sealId = request.tamperSealId().trim();
            if (!tamperSealService.verifyChecksum(sealId)) {
                throw new BadRequestException("Mã tem niêm phong không hợp lệ hoặc sai định dạng checksum");
            }
        } else if (order.getTamperSealId() != null && !order.getTamperSealId().isBlank()) {
            sealId = order.getTamperSealId();
        } else if ("PASSED".equals(request.status())) {
            sealId = tamperSealService.generateSealCode();
        }

        if (sealId != null) {
            order.setTamperSealId(sealId);
            if (inspectionSealLogRepository != null && !inspectionSealLogRepository.existsByTamperSealIdAndScanStage(sealId, "INITIAL_SEAL")) {
                User inspector = userRepository.findById(inspectorId).orElse(null);
                InspectionSealLog sealLog = new InspectionSealLog(
                        order,
                        sealId,
                        inspector,
                        "INITIAL_SEAL",
                        "INTACT",
                        "Trung tâm kiểm định SecondLife",
                        "Tem niêm phong QR dán trên thân máy sau khi KTV hoàn tất kiểm định"
                );
                inspectionSealLogRepository.save(sealLog);
            }
        }
        if (request.barcode() != null) {
            order.setBarcode(request.barcode());
        }

        if (request.checklistResults() != null && !request.checklistResults().isEmpty()) {
            try {
                order.setChecklistResultJson(objectMapper.writeValueAsString(request.checklistResults()));
            } catch (Exception ex) {
                log.warn("Không thể serialize checklist results sang JSON: {}", ex.getMessage());
            }
        }

        Post post = postRepository.findByIdForUpdate(order.getPost().getId())
                .orElseThrow(() -> new NotFoundException("Bài đăng không tồn tại"));

        if ("FAILED".equals(request.status())) {
            order.setStatus(InspectionOrderStatus.FAILED.name());
            if (!isExternalVideo) {
                order.setVideoRetentionUntil(Instant.now().plus(14, ChronoUnit.DAYS));
            }
            post.setStatus("ACTIVE"); // Mở lại bài đăng
            postRepository.save(post);

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
                        "/buyer/inspections/" + order.getId()
                    );
                }
                if (order.getSeller() != null) {
                    notificationService.notifyWithEmail(
                        order.getSeller().getId(),
                        "[Biên bản] Kết quả kiểm định KHÔNG ĐẠT - " + title,
                        "Sản phẩm \"" + title + "\" KHÔNG ĐẠT chuẩn kiểm định. Bài đăng đã được hoàn về trạng thái ACTIVE.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId()
                    );
                }
            }
        } else if ("PASSED".equals(request.status())) {
            order.setStatus(InspectionOrderStatus.PASSED.name());
            order.setFinalPaymentDeadline(Instant.now().plus(Duration.ofHours(6)));
            order.setFinalItemPrice(post.getPrice());

            // AI Scan lần 2 để xác nhận bài đăng nếu bài đăng đang chờ duyệt
            if ("PENDING_APPROVAL".equals(post.getStatus())) {
                String scanResult = aiScanPost(post);
                if ("APPROVED".equals(scanResult)) {
                    publication.activate(post);
                } else {
                    String reason = scanResult.contains(":") ? scanResult.substring(scanResult.indexOf(":") + 1).trim() : "AI final check failed";
                    post.setStatus("REJECTED");
                    post.setRejectionReason("Post failed final AI scan after inspection: " + reason);
                }
                postRepository.save(post);
            }

            if (notificationService != null) {
                String title = post.getTitle();
                if (order.getBuyer() != null) {
                    notificationService.notifyWithEmail(
                        order.getBuyer().getId(),
                        "[Biên bản] Kết quả kiểm định ĐẠT - " + title,
                        "Sản phẩm \"" + title + "\" đã được kiểm định ĐẠT CHUẨN. Bạn có 6 giờ để xác nhận thanh toán mua hàng.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/buyer/inspections/" + order.getId()
                    );
                }
                if (order.getSeller() != null) {
                    notificationService.notifyWithEmail(
                        order.getSeller().getId(),
                        "[Biên bản] Kết quả kiểm định ĐẠT - " + title,
                        "Sản phẩm \"" + title + "\" đã được kiểm định ĐẠT CHUẨN. Đang chờ người mua thanh toán tiền hàng trong 6 giờ.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId()
                    );
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

        UserProfile profile =
                new UserProfile(newInspector, fullName != null && !fullName.isBlank() ? fullName : email, null, null);
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

    /**
     * Gọi Google Gemini để scan nội dung bài đăng.
     * Returns "APPROVED" hoặc "REJECTED:<reason>"
     */
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
                post.getTitle(), post.getDescription(), post.getPrice()
        );

        Prompt aiPrompt = new Prompt(new UserMessage(prompt));
        String result;
        try {
            result = googleChatGateway.complete(aiPrompt);
        } catch (Exception ex) {
            throw new AiProviderException("AI moderation unavailable; no listing credit was consumed", ex);
        }
        if (result == null) throw new AiProviderException("AI moderation returned no result");
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
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tem niêm phong hợp lệ với mã: " + tamperSealId));

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
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin tem niêm phong: " + request.tamperSealId()));

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
                request.note()
        );

        sealLog = inspectionSealLogRepository.save(sealLog);

        if (notificationService != null && "TAMPERED".equalsIgnoreCase(request.sealCondition())) {
            if (order.getBuyer() != null) {
                notificationService.notify(
                        order.getBuyer().getId(),
                        "Cảnh báo tem niêm phong bị rách/bóc!",
                        "Tem niêm phong " + request.tamperSealId() + " được ghi nhận có dấu hiệu bị bóc ở giai đoạn " + request.scanStage() + ".",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION",
                        "/orders/" + order.getId()
                );
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
        String newSeal = tamperSealService.generateSealCode();
        order.setTamperSealId(newSeal);
        inspectionOrderRepository.save(order);
        return newSeal;
    }
}

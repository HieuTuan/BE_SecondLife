package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionAppointmentRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserRole;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.exception.AiProviderException;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;
import com.secondlife.secondlife.service.ListingPublicationService;
import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionServiceImpl implements InspectionService {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final PostRepository postRepository;
    private final com.secondlife.secondlife.repository.ShipmentRepository shipments;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final ListingPublicationService publication;
    private final InspectionPricingService inspectionPricingService;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @Qualifier("googleGenAiChatModel")
    private final org.springframework.ai.chat.model.ChatModel googleChatModel;

    @Override
    @Transactional
    public InspectionOrderResponse requestInspection(UUID buyerId, InspectionBuyRequest request) {
        Post post = postRepository.findById(request.getPostId())
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
                        .buyerLatitude(request.getBuyerLatitude())
                        .buyerLongitude(request.getBuyerLongitude())
                        .build()
        );

        InspectionOrderType orderType = request.getOrderType() != null ? request.getOrderType() : InspectionOrderType.AT_CENTER;
        InspectionFeePayer feePayer = request.getFeePayer() != null ? request.getFeePayer() : InspectionFeePayer.BUYER_PAYS;

        BigDecimal inspectionFee = quote.getBaseInspectionFee();
        BigDecimal shippingFee = BigDecimal.ZERO;
        BigDecimal travelDist = BigDecimal.ZERO;
        BigDecimal travelFee = BigDecimal.ZERO;
        BigDecimal returnReserve = BigDecimal.ZERO;
        BigDecimal totalFee;

        if (orderType == InspectionOrderType.AT_HOME) {
            travelDist = quote.getTravelDistanceKm();
            travelFee = quote.getTravelFeeRoundTrip();
            totalFee = quote.getAtHomeTotalFee();
        } else {
            shippingFee = quote.getShippingToCenterFee();
            returnReserve = quote.getReturnShippingReserveFee();
            totalFee = quote.getAtCenterTotalFee();
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

        order.setBuyerConsentZin(request.isBuyerConsentZinLoss());
        order.setSellerConsentZin(false);

        // Đặt bài đăng sang trạng thái RESERVED để tránh người khác đặt mua đè
        post.setStatus("RESERVED");
        postRepository.save(post);

        InspectionOrder saved = inspectionOrderRepository.save(order);
        log.info("Created inspection order {} for post {} by buyer {}", saved.getId(), post.getId(), buyerId);
        return InspectionOrderResponse.from(saved);
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

        String action = request.getAction() != null ? request.getAction().toUpperCase() : "";

        if ("REJECT".equals(action)) {
            order.setStatus(InspectionOrderStatus.CANCELLED.name());
            order.setNote("Người bán từ chối: " + (request.getRejectReason() != null ? request.getRejectReason() : "Không nêu lý do"));
            // Mở lại bài đăng về ACTIVE
            Post post = order.getPost();
            if (post != null && "RESERVED".equals(post.getStatus())) {
                post.setStatus("ACTIVE");
                postRepository.save(post);
            }
            return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
        }

        if ("COUNTER".equals(action)) {
            if (request.getCounterFeePayer() == null) {
                throw new BadRequestException("Cần chỉ định phương án phí đề xuất lại (counterFeePayer)");
            }
            order.setFeePayer(request.getCounterFeePayer());
            BigDecimal totalFee = order.getOrderType() == InspectionOrderType.AT_HOME
                    ? order.getInspectionFee().add(order.getTravelFee())
                    : order.getInspectionFee().add(order.getShippingFee()).add(order.getReturnShippingReserveFee());

            switch (request.getCounterFeePayer()) {
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
            order.setNote("Người bán đề xuất lại phương án chia phí: " + request.getCounterFeePayer());
            return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
        }

        if ("ACCEPT".equals(action)) {
            if (!request.isSellerConsentZinLoss()) {
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

            return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
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

        if (request.getAppointmentTime() == null || request.getAppointmentTime().isBefore(Instant.now())) {
            throw new BadRequestException("Thời gian hẹn phải nằm ở tương lai");
        }

        order.setAppointmentScheduledAt(request.getAppointmentTime());
        order.setStatus(InspectionOrderStatus.SCHEDULED.name());
        if (request.getNote() != null && !request.getNote().isBlank()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + "Lịch hẹn: " + request.getNote());
        }

        return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
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

        return InspectionOrderResponse.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getBuyerOrders(UUID buyerId, Pageable pageable) {
        return inspectionOrderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)
                .map(InspectionOrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getSellerOrders(UUID sellerId, Pageable pageable) {
        return inspectionOrderRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable)
                .map(InspectionOrderResponse::from);
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
            walletService.processPayment(buyerId, totalPayment, order.getId());
        }

        order.setFinalPaymentPaidAt(Instant.now());
        order.setStatus(InspectionOrderStatus.DELIVERING_TO_BUYER.name());

        Post post = order.getPost();
        if (post != null) {
            post.setStatus("SOLD");
            postRepository.save(post);
        }

        return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InspectionOrderResponse> getMyOrders(UUID inspectorId) {
        return inspectionOrderRepository.findByInspectorIdOrderByCreatedAtDesc(inspectorId)
                .stream()
                .map(InspectionOrderResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InspectionOrderResponse> getAllOrders(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return inspectionOrderRepository.findByStatus(status, pageable)
                    .map(InspectionOrderResponse::from);
        }
        return inspectionOrderRepository.findAll(pageable)
                .map(InspectionOrderResponse::from);
    }

    @Override
    @Transactional
    public InspectionOrderResponse assignInspector(UUID orderId, UUID inspectorId) {
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

        order.setInspector(inspector);
        order.setStatus(InspectionOrderStatus.DISPATCHED.name());
        return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
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

        order.setNote(request.getNote());
        order.setVideoProofUrl(request.getVideoProofUrl());
        order.setUnboxingVideoUrl(request.getUnboxingVideoUrl());

        if (request.getChecklistResults() != null && !request.getChecklistResults().isEmpty()) {
            try {
                order.setChecklistResultJson(objectMapper.writeValueAsString(request.getChecklistResults()));
            } catch (Exception ex) {
                log.warn("Không thể serialize checklist results sang JSON: {}", ex.getMessage());
            }
        }

        Post post = postRepository.findByIdForUpdate(order.getPost().getId())
                .orElseThrow(() -> new NotFoundException("Bài đăng không tồn tại"));

        if ("FAILED".equals(request.getStatus())) {
            order.setStatus(InspectionOrderStatus.FAILED.name());
            post.setStatus("ACTIVE"); // Mở lại bài đăng
            postRepository.save(post);
        } else if ("PASSED".equals(request.getStatus())) {
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
        } else {
            throw new BadRequestException("Trạng thái kết quả không hợp lệ. Phải là PASSED hoặc FAILED");
        }

        return InspectionOrderResponse.from(inspectionOrderRepository.save(order));
    }

    @Override
    @Transactional
    public void createInspectorAccount(String email, String fullName, String password, UUID managerId) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email đã được đăng ký trong hệ thống");
        }

        Role inspectorRole = roleRepository.findByCode("INSPECTOR")
                .orElseThrow(() -> new RuntimeException("Role INSPECTOR không tồn tại trong database"));

        User newInspector = new User();
        newInspector.setEmail(email);
        newInspector.setPasswordHash(passwordEncoder.encode(password));
        newInspector.setAccountStatus(AccountStatus.ACTIVE);
        newInspector.setEmailVerified(true);
        newInspector = userRepository.save(newInspector);

        UserRole userRole = new UserRole(newInspector, inspectorRole);
        newInspector.getUserRoles().add(userRole);
        userRepository.save(newInspector);
    }

    @Override
    @Transactional(readOnly = true)
    public List<?> getStaffList(UUID managerId) {
        return userRepository.findAll().stream()
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
        ChatClient client = ChatClient.builder(googleChatModel).build();
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
            result = client.prompt(aiPrompt).call().content();
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
}

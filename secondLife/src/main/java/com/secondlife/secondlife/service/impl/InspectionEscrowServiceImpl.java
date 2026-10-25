package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionEscrowService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class InspectionEscrowServiceImpl implements InspectionEscrowService {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final WalletService walletService;
    private final PostRepository postRepository;
    private final InspectionOrderMapper inspectionOrderMapper;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public InspectionEscrowServiceImpl(InspectionOrderRepository inspectionOrderRepository,
                                       WalletService walletService,
                                       PostRepository postRepository,
                                       InspectionOrderMapper inspectionOrderMapper,
                                       NotificationService notificationService,
                                       UserRepository userRepository) {
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.walletService = walletService;
        this.postRepository = postRepository;
        this.inspectionOrderMapper = inspectionOrderMapper;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public InspectionOrderResponse payInspectionFeeResponse(UUID userId, UUID inspectionOrderId) {
        return inspectionOrderMapper.toResponse(payInspectionFee(userId, inspectionOrderId));
    }

    @Override
    @Transactional
    public InspectionOrder payInspectionFee(UUID userId, UUID inspectionOrderId) {
        InspectionOrder order = inspectionOrderRepository.findByIdForUpdate(inspectionOrderId)
                .orElseThrow(() -> new NotFoundException("Inspection order not found"));

        if (!"WAITING_FEE_PAYMENT".equals(order.getStatus())) {
            throw new ConflictException("Đơn kiểm định không ở trạng thái chờ thanh toán phí");
        }

        if (order.getPaymentDeadline() != null && order.getPaymentDeadline().isBefore(Instant.now())) {
            refundExpiredOrder(order);
            throw new ConflictException("Thời hạn thanh toán phí kiểm định (6 giờ) đã hết");
        }

        boolean isBuyer = order.getBuyer() != null && order.getBuyer().getId().equals(userId);
        boolean isSeller = order.getSeller() != null && order.getSeller().getId().equals(userId);

        if (!isBuyer && !isSeller) {
            throw new ForbiddenException("Bạn không phải là người mua hoặc người bán của đơn kiểm định này");
        }

        if (isBuyer) {
            if (order.getBuyerPaidAt() != null) {
                throw new ConflictException("Người mua đã thanh toán phí kiểm định rồi");
            }
            BigDecimal amount = order.getBuyerFeeShare() != null ? order.getBuyerFeeShare() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                walletService.processPayment(userId, amount, order.getId(), WalletTransactionReferenceType.INSPECTION_ORDER);
            }
            order.setBuyerPaidAt(Instant.now());
        }

        if (isSeller) {
            if (order.getSellerPaidAt() != null) {
                throw new ConflictException("Người bán đã thanh toán phí kiểm định rồi");
            }
            BigDecimal amount = order.getSellerFeeShare() != null ? order.getSellerFeeShare() : BigDecimal.ZERO;
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                walletService.processPayment(userId, amount, order.getId(), WalletTransactionReferenceType.INSPECTION_ORDER);
            }
            order.setSellerPaidAt(Instant.now());
        }

        boolean buyerDone = (order.getBuyerFeeShare() == null || order.getBuyerFeeShare().compareTo(BigDecimal.ZERO) == 0) || order.getBuyerPaidAt() != null;
        boolean sellerDone = (order.getSellerFeeShare() == null || order.getSellerFeeShare().compareTo(BigDecimal.ZERO) == 0) || order.getSellerPaidAt() != null;

        if (buyerDone && sellerDone) {
            order.setStatus("CONFIRMED_PENDING_DISPATCH");
            log.info("Inspection order {} fully funded and transitioned to CONFIRMED_PENDING_DISPATCH", order.getId());

            if (notificationService != null && userRepository != null) {
                String postTitle = order.getPost() != null && order.getPost().getTitle() != null ? order.getPost().getTitle() : "";
                List<UUID> centerManagerIds = userRepository.findActiveInspectionCenterUserIds();
                for (UUID centerId : centerManagerIds) {
                    notificationService.notify(
                        centerId,
                        "Đơn kiểm định mới cần phân công KTV",
                        "Đơn kiểm định cho sản phẩm \"" + postTitle + "\" đã hoàn tất đóng phí và đang chờ bạn phân công Kỹ thuật viên.",
                        NotificationType.INSPECTION,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/inspector-center/orders"
                    );
                }
            }
        }

        return inspectionOrderRepository.save(order);
    }

    @Override
    @Transactional
    public void refundExpiredOrder(InspectionOrder order) {
        if (!"WAITING_FEE_PAYMENT".equals(order.getStatus())) return;

        log.info("Refunding and expiring unpaid inspection order: {}", order.getId());

        if (order.getBuyerPaidAt() != null && order.getBuyer() != null && order.getBuyerFeeShare() != null
                && order.getBuyerFeeShare().compareTo(BigDecimal.ZERO) > 0) {
            try {
                walletService.processRefund(order.getBuyer().getId(), order.getBuyerFeeShare(), order.getId(),
                        WalletTransactionReferenceType.INSPECTION_ORDER);
                log.info("Refunded {} VND to buyer {}", order.getBuyerFeeShare(), order.getBuyer().getId());
            } catch (Exception ex) {
                log.error("Failed to refund buyer {}: {}", order.getBuyer().getId(), ex.getMessage());
            }
        }

        if (order.getSellerPaidAt() != null && order.getSeller() != null && order.getSellerFeeShare() != null
                && order.getSellerFeeShare().compareTo(BigDecimal.ZERO) > 0) {
            try {
                walletService.processRefund(order.getSeller().getId(), order.getSellerFeeShare(), order.getId(),
                        WalletTransactionReferenceType.INSPECTION_ORDER);
                log.info("Refunded {} VND to seller {}", order.getSellerFeeShare(), order.getSeller().getId());
            } catch (Exception ex) {
                log.error("Failed to refund seller {}: {}", order.getSeller().getId(), ex.getMessage());
            }
        }

        order.setStatus("PAYMENT_EXPIRED");
        inspectionOrderRepository.save(order);

        if (order.getPost() != null) {
            Post post = postRepository.findById(order.getPost().getId()).orElse(null);
            if (post != null && "RESERVED".equals(post.getStatus())) {
                post.setStatus("ACTIVE");
                postRepository.save(post);
            }
        }
    }
}

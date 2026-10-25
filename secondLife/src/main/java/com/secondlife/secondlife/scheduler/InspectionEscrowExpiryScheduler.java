package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.service.impl.*;
import com.secondlife.secondlife.service.InspectionEscrowService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.WalletService;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class InspectionEscrowExpiryScheduler {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final InspectionEscrowService inspectionEscrowService;
    private final PostRepository postRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Autowired
    public InspectionEscrowExpiryScheduler(InspectionOrderRepository inspectionOrderRepository,
                                           InspectionEscrowService inspectionEscrowService,
                                           PostRepository postRepository,
                                           WalletService walletService,
                                           @Autowired(required = false) NotificationService notificationService) {
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.inspectionEscrowService = inspectionEscrowService;
        this.postRepository = postRepository;
        this.walletService = walletService;
        this.notificationService = notificationService;
    }

    /**
     * Quét định kỳ mỗi 60 giây các đơn kiểm định quá hạn 6 giờ chưa đủ tiền thanh toán.
     */
    @Scheduled(fixedRate = 60000)
    public void sweepExpiredInspectionOrders() {
        Instant now = Instant.now();
        List<InspectionOrder> expiredOrders = inspectionOrderRepository.findExpiredUnpaidOrders(now);

        if (!expiredOrders.isEmpty()) {
            log.info("Found {} expired unpaid inspection orders to process", expiredOrders.size());
            for (InspectionOrder order : expiredOrders) {
                try {
                    inspectionEscrowService.refundExpiredOrder(order);
                } catch (Exception ex) {
                    log.error("Error refunding expired inspection order {}: {}", order.getId(), ex.getMessage());
                }
            }
        }

        // Quét các đơn kiểm định đã PASSED nhưng Buyer quá hạn 6 giờ không thanh toán tiền hàng
        List<InspectionOrder> expiredFinalOrders = inspectionOrderRepository.findExpiredFinalPaymentOrders(now);
        if (!expiredFinalOrders.isEmpty()) {
            log.info("Found {} expired final payment inspection orders", expiredFinalOrders.size());
            for (InspectionOrder order : expiredFinalOrders) {
                try {
                    processExpiredFinalPayment(order);
                } catch (Exception ex) {
                    log.error("Error processing expired final payment order {}: {}", order.getId(), ex.getMessage());
                }
            }
        }
    }

    @Transactional
    public void processExpiredFinalPayment(InspectionOrder order) {
        log.warn("Buyer expired 6-hour payment window for passed inspection order: {}", order.getId());
        order.setStatus("CANCELLED");
        order.setNote((order.getNote() != null ? order.getNote() + " | " : "") +
                "Người mua không thanh toán tiền hàng trong vòng 6 giờ sau khi kiểm định đạt chuẩn.");

        // Bồi hoàn tiền cọc/phí kiểm định của người mua cho người bán
        if (order.getBuyerPaidAt() != null && order.getBuyer() != null && order.getSeller() != null
                && order.getBuyerFeeShare() != null && order.getBuyerFeeShare().compareTo(BigDecimal.ZERO) > 0) {
            try {
                walletService.processEarning(order.getSeller().getId(), order.getBuyerFeeShare(), order.getId(),
                        WalletTransactionReferenceType.INSPECTION_ORDER);
                log.info("Compensated buyer deposit fee share of {} to seller {} for cancelled inspection order {}",
                        order.getBuyerFeeShare(), order.getSeller().getId(), order.getId());

                if (notificationService != null) {
                    notificationService.notify(
                            order.getSeller().getId(),
                            "Nhận tiền bồi hoàn cọc kiểm định",
                            "Người mua không thanh toán tiền hàng đúng hạn 6 giờ. Bạn nhận được số tiền bồi hoàn cọc: " + order.getBuyerFeeShare() + " VND.",
                            NotificationType.ORDER,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/seller/inspections/" + order.getId()
                    );
                    notificationService.notify(
                            order.getBuyer().getId(),
                            "Đơn kiểm định đã bị hủy do quá hạn thanh toán",
                            "Bạn không hoàn tất thanh toán trong 6 giờ. Đơn hàng bị hủy và tiền cọc kiểm định được bồi hoàn cho người bán theo chính sách cam kết.",
                            NotificationType.ORDER,
                            order.getId(),
                            "INSPECTION_ORDER",
                            "/buyer/inspections/" + order.getId()
                    );
                }
            } catch (Exception ex) {
                log.error("Failed to compensate seller {} for expired final payment order {}: {}",
                        order.getSeller().getId(), order.getId(), ex.getMessage());
            }
        }

        inspectionOrderRepository.save(order);

        // Trả lại trạng thái bài đăng về ACTIVE cho người bán
        if (order.getPost() != null) {
            Post post = postRepository.findById(order.getPost().getId()).orElse(null);
            if (post != null && "RESERVED".equals(post.getStatus())) {
                post.setStatus("ACTIVE");
                postRepository.save(post);
            }
        }
    }
}

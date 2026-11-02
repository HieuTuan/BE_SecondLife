package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.InspectionEscrowService;
import com.secondlife.secondlife.service.InspectionFeeSettlementService;
import com.secondlife.secondlife.service.InspectionService;
import com.secondlife.secondlife.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
public class InspectionEscrowExpiryScheduler {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final InspectionEscrowService inspectionEscrowService;
    private final InspectionService inspectionService;
    private final InspectionFeeSettlementService feeSettlementService;
    private final PostRepository postRepository;
    private final NotificationService notificationService;

    @Autowired
    public InspectionEscrowExpiryScheduler(InspectionOrderRepository inspectionOrderRepository,
                                           InspectionEscrowService inspectionEscrowService,
                                           InspectionService inspectionService,
                                           InspectionFeeSettlementService feeSettlementService,
                                           PostRepository postRepository,
                                           @Autowired(required = false) NotificationService notificationService) {
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.inspectionEscrowService = inspectionEscrowService;
        this.inspectionService = inspectionService;
        this.feeSettlementService = feeSettlementService;
        this.postRepository = postRepository;
        this.notificationService = notificationService;
    }

    /**
     * Quét định kỳ mỗi 60 giây các đơn kiểm định quá hạn phản hồi (24h), quá hạn đóng phí (6h), hoặc quá hạn mua hàng (6h).
     */
    @Scheduled(fixedRate = 60000)
    public void sweepExpiredInspectionOrders() {
        // 1. Quét các đơn chờ phản hồi quá hạn 24h
        try {
            inspectionService.processExpiredApprovals();
        } catch (Exception ex) {
            log.error("Error sweeping expired inspection approvals: {}", ex.getMessage());
        }

        Instant now = Instant.now();
        // 2. Quét các đơn kiểm định quá hạn 6 giờ chưa đủ tiền thanh toán
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

        // 3. Quét các đơn kiểm định đã PASSED nhưng Buyer quá hạn 6 giờ không thanh toán tiền hàng
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

        // 4. Quét các đơn FAILED hết hạn 24 giờ khiếu nại để trừ điểm uy tín người bán
        try {
            inspectionService.processExpiredAppeals();
        } catch (Exception ex) {
            log.error("Error sweeping expired inspection appeals: {}", ex.getMessage());
        }
    }

    @Transactional
    public void processExpiredFinalPayment(InspectionOrder order) {
        log.warn("Buyer expired 6-hour payment window for passed inspection order: {}", order.getId());
        order.setStatus("CANCELLED");
        order.setNote((order.getNote() != null ? order.getNote() + " | " : "") +
                "Người mua không thanh toán tiền hàng trong vòng 6 giờ sau khi kiểm định đạt chuẩn.");

        feeSettlementService.settle(order, InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED,
                "Quá hạn thanh toán tiền hàng sau khi kiểm định đạt chuẩn (6 giờ)");

        inspectionOrderRepository.save(order);

        // Trả lại trạng thái bài đăng về ACTIVE cho người bán (hoặc giữ RESERVED nếu có negotiation còn hạn)
        if (order.getPost() != null) {
            Post post = postRepository.findById(order.getPost().getId()).orElse(null);
            if (post != null && "RESERVED".equals(post.getStatus())) {
                boolean hasActiveNegotiation = order.getNegotiation() != null
                        && order.getNegotiation().getStatus() == NegotiationStatus.ACCEPTED
                        && order.getNegotiation().getExpiredAt() != null
                        && order.getNegotiation().getExpiredAt().isAfter(Instant.now());
                if (!hasActiveNegotiation) {
                    post.setStatus("ACTIVE");
                    postRepository.save(post);
                }
            }
        }

        if (notificationService != null) {
            if (order.getSeller() != null) {
                notificationService.notify(
                        order.getSeller().getId(),
                        "Đơn kiểm định đã hủy do quá hạn thanh toán",
                        "Người mua không thanh toán tiền hàng đúng hạn 6 giờ.",
                        NotificationType.ORDER,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/seller/inspections/" + order.getId()
                );
            }
            if (order.getBuyer() != null) {
                notificationService.notify(
                        order.getBuyer().getId(),
                        "Đơn kiểm định đã bị hủy do quá hạn thanh toán",
                        "Bạn không hoàn tất thanh toán trong 6 giờ. Đơn hàng đã bị hủy theo chính sách sàn.",
                        NotificationType.ORDER,
                        order.getId(),
                        "INSPECTION_ORDER",
                        "/buyer/inspections/" + order.getId()
                );
            }
        }
    }
}

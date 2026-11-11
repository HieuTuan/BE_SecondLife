package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.service.InspectionEscrowService;
import com.secondlife.secondlife.service.InspectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class InspectionEscrowExpiryScheduler {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final InspectionEscrowService inspectionEscrowService;
    private final InspectionService inspectionService;

    @Autowired
    public InspectionEscrowExpiryScheduler(InspectionOrderRepository inspectionOrderRepository,
                                           InspectionEscrowService inspectionEscrowService,
                                           InspectionService inspectionService) {
        this.inspectionOrderRepository = inspectionOrderRepository;
        this.inspectionEscrowService = inspectionEscrowService;
        this.inspectionService = inspectionService;
    }

    /**
     * Quét định kỳ mỗi 60 giây các đơn kiểm định quá hạn phản hồi (24h), quá hạn đóng phí theo paymentDeadline, hoặc quá hạn mua hàng (6h).
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
        // 2. Quét các đơn kiểm định đã qua paymentDeadline mà chưa đóng đủ phí
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
        List<UUID> expiredFinalOrders = inspectionOrderRepository.findExpiredFinalPaymentOrderIds(now);
        if (!expiredFinalOrders.isEmpty()) {
            log.info("Found {} expired final payment inspection orders", expiredFinalOrders.size());
            for (UUID orderId : expiredFinalOrders) {
                try {
                    inspectionService.processExpiredFinalPayment(orderId);
                } catch (Exception ex) {
                    log.error("Error processing expired final payment order {}", orderId, ex);
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
}

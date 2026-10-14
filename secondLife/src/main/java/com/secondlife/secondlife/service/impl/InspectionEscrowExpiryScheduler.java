package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.InspectionEscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InspectionEscrowExpiryScheduler {

    private final InspectionOrderRepository inspectionOrderRepository;
    private final InspectionEscrowService inspectionEscrowService;
    private final PostRepository postRepository;

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

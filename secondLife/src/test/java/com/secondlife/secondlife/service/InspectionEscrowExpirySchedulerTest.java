package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.scheduler.InspectionEscrowExpiryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionEscrowExpirySchedulerTest {

    @Mock
    private InspectionOrderRepository inspectionOrderRepository;

    @Mock
    private InspectionEscrowService inspectionEscrowService;

    @Mock
    private InspectionService inspectionService;

    @Mock
    private InspectionFeeSettlementService feeSettlementService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private NotificationService notificationService;

    private InspectionEscrowExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InspectionEscrowExpiryScheduler(
                inspectionOrderRepository,
                inspectionEscrowService,
                inspectionService,
                feeSettlementService,
                postRepository,
                notificationService
        );
    }

    @Test
    @DisplayName("processExpiredFinalPayment: Hủy đơn, hoàn post về ACTIVE và bồi hoàn cọc cho người bán qua feeSettlementService")
    void testProcessExpiredFinalPayment() {
        UUID sellerId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        User seller = new User();
        seller.setId(sellerId);

        User buyer = new User();
        buyer.setId(buyerId);

        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setStatus("RESERVED");

        InspectionOrder order = new InspectionOrder();
        order.setId(UUID.randomUUID());
        order.setStatus("PASSED");
        order.setSeller(seller);
        order.setBuyer(buyer);
        order.setPost(post);
        order.setBuyerPaidAt(Instant.now().minusSeconds(3600));
        order.setBuyerFeeShare(new BigDecimal("150000"));

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        scheduler.processExpiredFinalPayment(order);

        assertEquals("CANCELLED", order.getStatus());
        assertEquals("ACTIVE", post.getStatus());
        verify(feeSettlementService, times(1)).settle(eq(order), eq(InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED), any());
        verify(inspectionOrderRepository, times(1)).save(order);
        verify(postRepository, times(1)).save(post);
    }
}

package com.secondlife.secondlife.service;

import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.scheduler.InspectionEscrowExpiryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionEscrowExpirySchedulerTest {

    @Mock
    private InspectionOrderRepository inspectionOrderRepository;

    @Mock
    private InspectionEscrowService inspectionEscrowService;

    @Mock
    private InspectionService inspectionService;

    private InspectionEscrowExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new InspectionEscrowExpiryScheduler(
                inspectionOrderRepository,
                inspectionEscrowService,
                inspectionService
        );
    }

    @Test
    @DisplayName("Scheduler chuyển ID đơn hết hạn sang service quyết toán")
    void delegatesExpiredFinalPaymentToInspectionService() {
        UUID orderId = UUID.randomUUID();
        when(inspectionOrderRepository.findExpiredFinalPaymentOrderIds(any())).thenReturn(List.of(orderId));

        scheduler.sweepExpiredInspectionOrders();

        verify(inspectionService).processExpiredFinalPayment(orderId);
    }
}

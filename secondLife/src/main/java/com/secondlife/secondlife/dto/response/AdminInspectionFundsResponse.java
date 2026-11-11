package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminInspectionFundsResponse(
        UUID inspectionOrderId,
        String orderStatus,
        InspectionScope inspectionScope,
        InspectionOrderType orderType,
        InspectionFeePayer feePayer,
        boolean cashShippingSimulation,
        String currency,
        BigDecimal inspectionFee,
        BigDecimal travelFee,
        BigDecimal shippingFee,
        BigDecimal buyerCommitmentDeposit,
        Payment buyer,
        Payment seller,
        CollectionStatus collectionStatus,
        BigDecimal totalCollected,
        @Schema(description = "Collected inspection funds still awaiting internal allocation, refund or cash reimbursement. "
                + "Includes the held return reserve when applicable. Excludes purchase escrow and does not confirm carrier payment.")
        BigDecimal unsettledCollectedAmount,
        @Schema(description = "The collected portion of outbound cash shipping still awaiting reimbursement or refund")
        BigDecimal outboundCashPendingAmount,
        ReturnReserve returnReserve,
        Settlement settlement,
        List<Shipping> shipments
) {
    public record Payment(BigDecimal amountDue, BigDecimal amountCollected, Instant paidAt) {}

    public record ReturnReserve(BigDecimal plannedAmount, BigDecimal heldAmount, ReturnReserveStatus status) {}

    public record Settlement(
            InspectionSettlementOutcome outcome,
            UUID centerRecipientUserId,
            BigDecimal centerPayoutAmount,
            BigDecimal outboundShippingAllocated,
            @Schema(description = "Reserved amount before return reconciliation, or actual return cost after reconciliation")
            BigDecimal returnReserveAllocated,
            BigDecimal buyerRefundAmount,
            BigDecimal sellerRefundAmount,
            BigDecimal sellerCompensationAmount,
            BigDecimal platformRetainedAmount,
            Instant settledAt,
            Instant returnShippingSettledAt
    ) {}

    public record Shipping(
            UUID id,
            String leg,
            String provider,
            String orderCode,
            String status,
            String providerStatus,
            BigDecimal actualFee,
            UUID cashPayerId,
            BigDecimal cashPaidAmount,
            BigDecimal cashReimbursedAmount,
            Instant cashConfirmedAt
    ) {}

    public enum CollectionStatus {
        NOT_DUE, NO_PAYMENT_REQUIRED, UNPAID, PARTIALLY_PAID, PAID
    }

    public enum ReturnReserveStatus {
        NOT_REQUIRED, NOT_COLLECTED, HELD_PENDING_INSPECTION, REFUNDED_UNUSED,
        AWAITING_RETURN_SHIPMENT, RETURN_IN_PROGRESS, AWAITING_ACTUAL_FEE,
        AWAITING_CASH_CONFIRMATION, AWAITING_RECONCILIATION, EXCEEDS_RESERVE, SETTLED
    }
}

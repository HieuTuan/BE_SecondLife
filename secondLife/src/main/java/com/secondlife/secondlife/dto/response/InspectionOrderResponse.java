package com.secondlife.secondlife.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.secondlife.secondlife.enums.InspectionAppointmentShift;
import com.secondlife.secondlife.enums.InspectionAppointmentStatus;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record InspectionOrderResponse(
    UUID id,
    UUID postId,
    String postTitle,
    List<String> postImageUrls,
    BigDecimal postPrice,
    UUID buyerId,
    String buyerEmail,
    String buyerName,
    UUID sellerId,
    String sellerEmail,
    String sellerName,
    UUID inspectorId,
    String inspectorName,
    UUID linkedOrderId,
    UUID negotiationId,
    String inspectionScope,
    InspectionOrderType orderType,
    InspectionFeePayer feePayer,
    String status,
    String note,
    BigDecimal inspectionFee,
    BigDecimal shippingFee,
    BigDecimal travelDistanceKm,
    BigDecimal travelFee,
    BigDecimal returnShippingReserveFee,
    BigDecimal buyerFeeShare,
    BigDecimal sellerFeeShare,
    BigDecimal buyerCommitmentDeposit,
    BigDecimal initialDepositPaid,
    boolean finalPaymentRequired,
    Instant approvalDeadline,
    Instant paymentDeadline,
    Instant buyerPaidAt,
    Instant sellerPaidAt,
    Instant feeSettledAt,
    Instant appointmentScheduledAt,
    InspectionAppointmentShift appointmentShift,
    String appointmentNote,
    InspectionAppointmentStatus appointmentStatus,
    Instant counterScheduledAt,
    InspectionAppointmentShift counterShift,
    String counterNote,
    String appointmentRejectionReason,
    Instant homeCheckedInAt,
    List<String> evidencePhotoUrls,
    String checklistResultJson,
    String videoProofUrl,
    String unboxingVideoUrl,
    String videoCleanupStatus, // ACTIVE, CLEANED, EXEMPTED
    String videoCleanedMessage,
    Instant videoCleanedAt,
    Instant videoRetentionUntil,
    boolean buyerConsentZin,
    boolean sellerConsentZin,
    Instant finalPaymentDeadline,
    Instant finalPaymentPaidAt,
    BigDecimal finalItemPrice,
    BigDecimal finalShippingFee,
    Integer conditionScore,
    String conditionGrade,
    String tamperSealId,
    String barcode,
    Instant appealDeadline,
    String appealStatus,
    String appealReason,
    boolean reputationPenaltyApplied,
    BigDecimal reputationPenaltyPoints,
    Instant createdAt,
    Instant updatedAt,
    UUID inspectionCenterId,
    UUID checklistTemplateId,
    UUID feeProposalId,
    InspectionComplexityLevel complexityLevel,
    BigDecimal platformFeeRateSnapshot,
    UUID inspectionShippingQuoteId,
    String sellerPhone,
    String sellerAddress,
    String buyerPhone,
    String buyerAddress,
    String inspectionCenterName,
    String inspectionCenterAddress,
    String inspectionCenterPhone,
    boolean cashShippingSimulation,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Settled amount credited to the authenticated inspection center in VND. "
            + "Zero means the settlement allocated no payout. Omitted when no settlement belongs to this center.")
    BigDecimal centerPayoutAmount
) {
    @JsonProperty("customerStatus")
    public String customerStatus() {
        if (orderType != InspectionOrderType.AT_HOME && inspectorId != null && (InspectionOrderStatus.DISPATCHED.name().equals(status)
                || InspectionOrderStatus.SCHEDULED.name().equals(status))) {
            return InspectionOrderStatus.INSPECTING.name();
        }
        return status;
    }
}

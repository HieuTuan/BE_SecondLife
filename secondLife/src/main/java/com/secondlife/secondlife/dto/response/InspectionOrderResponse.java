package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderType;
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
    BigDecimal initialDepositPaid,
    boolean finalPaymentRequired,
    Instant paymentDeadline,
    Instant buyerPaidAt,
    Instant sellerPaidAt,
    Instant appointmentScheduledAt,
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
    Instant createdAt,
    Instant updatedAt
) {
}

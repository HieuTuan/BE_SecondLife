package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionOrderResponse {
    private UUID id;
    private UUID postId;
    private String postTitle;
    private List<String> postImageUrls;
    private BigDecimal postPrice;

    private UUID buyerId;
    private String buyerEmail;
    private String buyerName;

    private UUID sellerId;
    private String sellerEmail;
    private String sellerName;

    private UUID inspectorId;
    private String inspectorName;

    private InspectionOrderType orderType;
    private InspectionFeePayer feePayer;
    private String status;
    private String note;

    private BigDecimal inspectionFee;
    private BigDecimal shippingFee;
    private BigDecimal travelDistanceKm;
    private BigDecimal travelFee;
    private BigDecimal returnShippingReserveFee;

    private BigDecimal buyerFeeShare;
    private BigDecimal sellerFeeShare;

    private Instant paymentDeadline;
    private Instant buyerPaidAt;
    private Instant sellerPaidAt;

    private Instant appointmentScheduledAt;

    private String checklistResultJson;
    private String videoProofUrl;
    private String unboxingVideoUrl;

    private boolean buyerConsentZin;
    private boolean sellerConsentZin;

    private Instant finalPaymentDeadline;
    private Instant finalPaymentPaidAt;
    private BigDecimal finalItemPrice;
    private BigDecimal finalShippingFee;

    private Instant createdAt;
    private Instant updatedAt;

    public static InspectionOrderResponse from(InspectionOrder order) {
        if (order == null) return null;
        InspectionOrderResponse res = new InspectionOrderResponse();
        res.setId(order.getId());
        if (order.getPost() != null) {
            res.setPostId(order.getPost().getId());
            res.setPostTitle(order.getPost().getTitle());
            res.setPostImageUrls(order.getPost().getImageUrls());
            res.setPostPrice(order.getPost().getPrice());
        }
        if (order.getBuyer() != null) {
            res.setBuyerId(order.getBuyer().getId());
            res.setBuyerEmail(order.getBuyer().getEmail());
            res.setBuyerName(order.getBuyer().getProfile() != null ? order.getBuyer().getProfile().getFullName() : order.getBuyer().getEmail());
        }
        if (order.getSeller() != null) {
            res.setSellerId(order.getSeller().getId());
            res.setSellerEmail(order.getSeller().getEmail());
            res.setSellerName(order.getSeller().getProfile() != null ? order.getSeller().getProfile().getFullName() : order.getSeller().getEmail());
        }
        if (order.getInspector() != null) {
            res.setInspectorId(order.getInspector().getId());
            res.setInspectorName(order.getInspector().getProfile() != null && order.getInspector().getProfile().getFullName() != null
                    ? order.getInspector().getProfile().getFullName()
                    : order.getInspector().getEmail());
        }
        res.setOrderType(order.getOrderType());
        res.setFeePayer(order.getFeePayer());
        res.setStatus(order.getStatus());
        res.setNote(order.getNote());

        res.setInspectionFee(order.getInspectionFee());
        res.setShippingFee(order.getShippingFee());
        res.setTravelDistanceKm(order.getTravelDistanceKm());
        res.setTravelFee(order.getTravelFee());
        res.setReturnShippingReserveFee(order.getReturnShippingReserveFee());

        res.setBuyerFeeShare(order.getBuyerFeeShare());
        res.setSellerFeeShare(order.getSellerFeeShare());

        res.setPaymentDeadline(order.getPaymentDeadline());
        res.setBuyerPaidAt(order.getBuyerPaidAt());
        res.setSellerPaidAt(order.getSellerPaidAt());

        res.setAppointmentScheduledAt(order.getAppointmentScheduledAt());
        res.setChecklistResultJson(order.getChecklistResultJson());
        res.setVideoProofUrl(order.getVideoProofUrl());
        res.setUnboxingVideoUrl(order.getUnboxingVideoUrl());

        res.setBuyerConsentZin(order.isBuyerConsentZin());
        res.setSellerConsentZin(order.isSellerConsentZin());

        res.setFinalPaymentDeadline(order.getFinalPaymentDeadline());
        res.setFinalPaymentPaidAt(order.getFinalPaymentPaidAt());
        res.setFinalItemPrice(order.getFinalItemPrice());
        res.setFinalShippingFee(order.getFinalShippingFee());

        res.setCreatedAt(order.getCreatedAt());
        res.setUpdatedAt(order.getUpdatedAt());
        return res;
    }
}

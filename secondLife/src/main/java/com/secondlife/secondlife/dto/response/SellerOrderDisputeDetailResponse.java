package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SellerOrderDisputeDetailResponse(
        UUID id, ReasonCode reasonCode, String description, List<String> buyerEvidenceUrls,
        Status status, ResponsibleParty responsibleParty, String decisionReason, Instant resolvedAt,
        UUID orderId, UUID postId, ResolutionAction resolutionAction, Instant refundedAt,
        BigDecimal refundAmount, UUID returnShipmentId, Instant sellerResponseDeadlineAt,
        Instant sellerRespondedAt, Instant createdAt
) {
    public SellerOrderDisputeDetailResponse(UUID id, ReasonCode reasonCode, String description, List<String> buyerEvidenceUrls) {
        this(id, reasonCode, description, buyerEvidenceUrls, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}

package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.dto.request.ReportRequests.TargetType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportResponse(UUID id, UUID requestId, UUID reporterId, UUID sellerId, UUID postId, UUID orderId,
        ReasonCode reasonCode, String description, List<String> evidenceUrls, Status status, UUID reviewerId,
        ResponsibleParty responsibleParty, String decisionReason, Instant createdAt, Instant updatedAt, Instant resolvedAt,
        UUID relatedPostId, TargetType targetType, ResolutionAction resolutionAction, UUID returnShipmentId,
        Instant returnApprovedAt, Instant refundedAt, UUID refundedBy, BigDecimal refundAmount) {

    public ReportResponse(UUID id, UUID requestId, UUID reporterId, UUID sellerId, UUID postId, UUID orderId,
            ReasonCode reasonCode, String description, List<String> evidenceUrls, Status status, UUID reviewerId,
            ResponsibleParty responsibleParty, String decisionReason, Instant createdAt, Instant updatedAt,
            Instant resolvedAt, UUID relatedPostId, TargetType targetType) {
        this(id, requestId, reporterId, sellerId, postId, orderId, reasonCode, description, evidenceUrls, status,
                reviewerId, responsibleParty, decisionReason, createdAt, updatedAt, resolvedAt, relatedPostId,
                targetType, ResolutionAction.NONE, null, null, null, null, null);
    }
}

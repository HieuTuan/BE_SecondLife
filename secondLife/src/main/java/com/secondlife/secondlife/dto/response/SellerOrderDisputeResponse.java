package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;

import java.time.Instant;
import java.util.UUID;

public record SellerOrderDisputeResponse(UUID id, UUID orderId, String postTitle, ReasonCode reasonCode,
        Status status, ResolutionAction resolutionAction, Instant refundedAt, Instant createdAt,
        Instant sellerResponseDeadlineAt, Instant sellerRespondedAt,
        UUID postId, ResponsibleParty responsibleParty, String decisionReason, Instant resolvedAt) {

    public SellerOrderDisputeResponse(UUID id, UUID orderId, String postTitle, ReasonCode reasonCode,
            Status status, ResolutionAction resolutionAction, Instant refundedAt, Instant createdAt,
            Instant sellerResponseDeadlineAt, Instant sellerRespondedAt) {
        this(id, orderId, postTitle, reasonCode, status, resolutionAction, refundedAt, createdAt,
                sellerResponseDeadlineAt, sellerRespondedAt, null, null, null, null);
    }
}

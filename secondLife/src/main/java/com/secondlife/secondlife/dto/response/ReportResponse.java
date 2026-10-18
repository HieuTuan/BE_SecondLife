package com.secondlife.secondlife.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static com.secondlife.secondlife.dto.request.ReportRequests.*;
public record ReportResponse(UUID id, UUID requestId, UUID reporterId, UUID sellerId, UUID postId, UUID orderId,
        ReasonCode reasonCode, String description, List<String> evidenceUrls, Status status, UUID reviewerId,
        ResponsibleParty responsibleParty, String decisionReason, Instant createdAt, Instant updatedAt, Instant resolvedAt, UUID relatedPostId, TargetType targetType) { }

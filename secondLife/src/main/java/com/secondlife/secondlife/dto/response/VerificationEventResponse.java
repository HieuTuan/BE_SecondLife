package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.SellerVerificationEvent;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.enums.VerificationEventType;

import java.time.Instant;
import java.util.UUID;

public record VerificationEventResponse(
        UUID id,
        VerificationEventType eventType,
        SellerVerificationStatus fromStatus,
        SellerVerificationStatus toStatus,
        ReasonCode reasonCode,
        String actorType,
        UUID actorUserId,
        String notes,
        Instant createdAt
) {
    public static VerificationEventResponse from(SellerVerificationEvent event) {
        return new VerificationEventResponse(
                event.getId(),
                event.getEventType(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getReasonCode(),
                event.getActorType(),
                event.getActorUser() != null ? event.getActorUser().getId() : null,
                event.getNotes(),
                event.getCreatedAt()
        );
    }
}

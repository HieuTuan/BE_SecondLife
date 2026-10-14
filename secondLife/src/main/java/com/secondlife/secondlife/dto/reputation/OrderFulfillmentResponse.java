package com.secondlife.secondlife.dto.reputation;
import java.time.Instant;
import java.util.UUID;
public record OrderFulfillmentResponse(UUID orderId,Instant paidAt,Instant handoverDeadline,
        Instant sellerReadyAt,Instant carrierPickedUpAt,boolean lateHandoverCandidate,
        String cancellationReasonCode,UUID cancelledBy,String returnResponsibleParty) { }

package com.secondlife.secondlife.dto.response;
import java.time.Instant;
import java.util.UUID;
public record OrderFulfillmentResponse(UUID orderId,Instant paidAt,Instant handoverDeadline,
        Instant sellerReadyAt,Instant carrierPickedUpAt,boolean lateHandoverCandidate,
        String cancellationReasonCode,UUID cancelledBy,String returnResponsibleParty,
        Instant sellerConfirmedAt,Instant confirmationDeadline,Instant confirmationCancelAt) { }

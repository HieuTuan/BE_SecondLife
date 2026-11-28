package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.NegotiationStatus;

import java.time.Instant;
import java.util.UUID;

public interface ListingPublicationService {
    static String resolveRestoredStatusForNegotiation(InspectionOrder inspection, Instant now) {
        boolean hasActiveNegotiation = inspection != null
                && inspection.getNegotiation() != null
                && inspection.getNegotiation().getStatus() == NegotiationStatus.ACCEPTED
                && inspection.getNegotiation().getExpiredAt() != null
                && inspection.getNegotiation().getExpiredAt().isAfter(now);
        return hasActiveNegotiation ? "RESERVED" : "ACTIVE";
    }

    void requirePublishAvailable(UUID userId);

    void snapshotPolicy(Post post);

    void accept(Post post);

    void activate(Post post);

    void markReadyToPublish(Post post);

    void markReadyAfterInspectionReturn(UUID postId);

    void restoreAfterInspectionReturn(UUID inspectionId);

    Post publishInspectedPost(UUID sellerId, UUID postId);

}

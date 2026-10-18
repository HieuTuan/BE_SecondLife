package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class SellerReputationData {
    private SellerReputationData() { }

    public record State(long good, long bad, long incidents, long serious, boolean restricted,
                        Instant updated, BigDecimal penalty) { }

    public record Metrics(long cancellations, long returns, long nfr, long handovers, long late) { }

    public record Event(UUID id, UUID sellerId, String type, UUID orderId, UUID reportId,
                        UUID actorId, String reason, State before, State after,
                        BigDecimal scoreBefore, BigDecimal scoreAfter, String formulaVersion) { }
}

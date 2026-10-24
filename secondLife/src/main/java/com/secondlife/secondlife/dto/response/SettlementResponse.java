package com.secondlife.secondlife.dto.response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record SettlementResponse(UUID settlementId, UUID orderId, UUID commissionSnapshotId, UUID buyerId, UUID sellerId,
        BigDecimal commissionBase, BigDecimal rawCommission, BigDecimal platformCommission, BigDecimal sellerPayout,
        BigDecimal shippingFee, String currency, String roundingMode, Instant settledAt) {

}

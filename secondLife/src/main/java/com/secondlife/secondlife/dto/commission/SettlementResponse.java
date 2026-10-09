package com.secondlife.secondlife.dto.commission;
import com.secondlife.secondlife.entity.OrderSettlement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record SettlementResponse(UUID settlementId, UUID orderId, UUID commissionSnapshotId, UUID buyerId, UUID sellerId,
        BigDecimal commissionBase, BigDecimal rawCommission, BigDecimal platformCommission, BigDecimal sellerPayout,
        BigDecimal shippingFee, String currency, String roundingMode, Instant settledAt) {
    public static SettlementResponse from(OrderSettlement s) {
        return new SettlementResponse(s.getId(), s.getOrderId(), s.getCommissionSnapshotId(), s.getBuyerId(), s.getSellerId(),
                s.getCommissionBase(), s.getRawCommission(), s.getPlatformCommission(), s.getSellerPayout(),
                s.getShippingFee(), s.getCurrency(), s.getRoundingMode(), s.getSettledAt());
    }
}

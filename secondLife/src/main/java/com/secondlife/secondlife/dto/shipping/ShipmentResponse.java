package com.secondlife.secondlife.dto.shipping;
import com.secondlife.secondlife.entity.Shipment;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
public record ShipmentResponse(UUID shipmentId, UUID orderId, UUID inspectionOrderId, String leg, String orderCode,
        String status, String providerStatus, BigDecimal quotedFee, BigDecimal actualFee, Instant expectedDeliveryTime,
        Instant deliveredAt, String reason, String lastError, String podUrl) {
    public static ShipmentResponse from(Shipment s) { return new ShipmentResponse(s.getId(),s.getOrderId(),s.getInspectionOrderId(),
        s.getLeg(),s.getOrderCode(),s.getStatus(),s.getProviderStatus(),s.getQuotedFee(),s.getActualFee(),s.getExpectedDeliveryTime(),
        s.getDeliveredAt(),s.getReason(),s.getLastError(),s.getPodUrl()); }
}

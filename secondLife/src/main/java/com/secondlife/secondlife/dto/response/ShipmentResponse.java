package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record ShipmentResponse(UUID shipmentId, UUID orderId, UUID inspectionOrderId, String leg, String orderCode,
        String status, String providerStatus, BigDecimal quotedFee, BigDecimal actualFee, Instant expectedDeliveryTime,
        Instant deliveredAt, String reason, String lastError, String podUrl, String provider, String driverId,
        String shareLink, UUID id, String trackingUrl, String senderName, String senderPhone, String senderAddress,
        String receiverName, String receiverPhone, String receiverAddress) {

}

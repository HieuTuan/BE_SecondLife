package com.secondlife.secondlife.service;
import com.secondlife.secondlife.dto.commission.*;
import com.secondlife.secondlife.entity.Order;
import java.util.UUID;
public interface SettlementService {
    SettlementResponse settle(Order order);
    OrderCommissionResponse getCommission(UUID actorId, boolean canReadAny, UUID orderId);
    SettlementResponse getSettlement(UUID actorId, boolean canReadAny, UUID orderId);
    OrderCommissionResponse captureLegacy(UUID actorId, UUID orderId, String reason);
}

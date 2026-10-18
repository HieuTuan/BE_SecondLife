package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import java.util.UUID;

public interface InspectionEscrowService {
    InspectionOrderResponse payInspectionFeeResponse(UUID userId, UUID inspectionOrderId);
    InspectionOrder payInspectionFee(UUID userId, UUID inspectionOrderId);
    void refundExpiredOrder(InspectionOrder order);
}

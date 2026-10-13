package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionOrder;

import java.util.UUID;

public interface InspectionEscrowService {
    InspectionOrder payInspectionFee(UUID userId, UUID inspectionOrderId);
    void refundExpiredOrder(InspectionOrder order);
}

package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface InspectionFeeSettlementService {
    Map<UUID, BigDecimal> getCenterPayouts(UUID centerId, Collection<UUID> inspectionOrderIds);

    void settleReturnShipping(InspectionOrder inspectionOrder, BigDecimal actualFee);

    BigDecimal reimburseCashShipping(InspectionOrder inspectionOrder, Shipment shipment, BigDecimal paidAmount);

    InspectionFeeSettlement settle(InspectionOrder inspectionOrder, InspectionSettlementOutcome outcome, String reason);
}


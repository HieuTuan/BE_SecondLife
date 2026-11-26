package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.InspectionReturnFeeResponse;
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

    InspectionReturnFeeResponse previewReturnFee(InspectionOrder order, BigDecimal quoteFee);

    InspectionReturnFeeResponse getReturnFee(UUID actorId, boolean staff, UUID inspectionId);

    InspectionReturnFeeResponse payReturnFee(UUID actorId, UUID inspectionId, BigDecimal maxAmount);

    void chargeReturnQuote(InspectionOrder order, Shipment shipment);

    void clearCancelledReturnQuote(InspectionOrder order, Shipment shipment);

    void settleReturnShipping(InspectionOrder inspectionOrder, BigDecimal actualFee);

    boolean reimburseDeliveredReturn(InspectionOrder inspectionOrder, Shipment shipment, BigDecimal actualFee);

    BigDecimal reimburseCashShipping(InspectionOrder inspectionOrder, Shipment shipment, BigDecimal paidAmount);

    InspectionFeeSettlement settle(InspectionOrder inspectionOrder, InspectionSettlementOutcome outcome, String reason);
}


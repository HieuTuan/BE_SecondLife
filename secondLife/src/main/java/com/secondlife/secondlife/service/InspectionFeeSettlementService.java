package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;

import java.math.BigDecimal;

public interface InspectionFeeSettlementService {
    void settleReturnShipping(InspectionOrder inspectionOrder, BigDecimal actualFee);

    InspectionFeeSettlement settle(InspectionOrder inspectionOrder, InspectionSettlementOutcome outcome, String reason);
}


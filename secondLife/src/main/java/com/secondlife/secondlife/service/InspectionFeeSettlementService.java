package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;

public interface InspectionFeeSettlementService {
    InspectionFeeSettlement settle(InspectionOrder inspectionOrder, InspectionSettlementOutcome outcome, String reason);
}


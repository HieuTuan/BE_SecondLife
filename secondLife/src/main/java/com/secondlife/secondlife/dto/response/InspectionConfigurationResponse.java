package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionComplexityLevel;

import java.math.BigDecimal;
import java.util.UUID;

public record InspectionConfigurationResponse(
        UUID inspectionCenterId,
        UUID checklistTemplateId,
        InspectionComplexityLevel complexityLevel,
        UUID feeProposalId,
        BigDecimal inspectionFee,
        InspectionChecklistResponse checklist,
        BigDecimal platformFeeRate
) {
    public InspectionConfigurationResponse(UUID inspectionCenterId, UUID checklistTemplateId,
            InspectionComplexityLevel complexityLevel, UUID feeProposalId, BigDecimal inspectionFee,
            InspectionChecklistResponse checklist) {
        this(inspectionCenterId, checklistTemplateId, complexityLevel, feeProposalId, inspectionFee, checklist, null);
    }
}

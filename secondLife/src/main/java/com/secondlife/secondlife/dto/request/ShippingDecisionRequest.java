package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record ShippingDecisionRequest(@NotBlank @Size(max=2000) String reason,
    @Schema(description="Confirmed responsibility: SELLER counts as a failed transaction; omitted means not determined")
    com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty responsibleParty) {}

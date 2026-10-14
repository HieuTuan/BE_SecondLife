package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.constraints.*;
public record ShippingDecisionRequest(@NotBlank @Size(max=2000) String reason,
    @io.swagger.v3.oas.annotations.media.Schema(description="Confirmed responsibility: SELLER counts as a failed transaction; omitted means not determined")
    com.secondlife.secondlife.dto.reputation.ReportRequests.ResponsibleParty responsibleParty) {}

package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;

public record InspectionRejectPurchaseRequest(
    @JsonAlias({"rejectionReason", "cancelReason"})
    String reason
) {}

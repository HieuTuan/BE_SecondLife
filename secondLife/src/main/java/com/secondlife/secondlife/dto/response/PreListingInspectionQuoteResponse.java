package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionOrderType;
import java.time.Instant;
import java.util.UUID;

public record PreListingInspectionQuoteResponse(
        UUID quoteId, Instant expiresAt, InspectionOrderType orderType, InspectionQuoteResponse quote) {
}

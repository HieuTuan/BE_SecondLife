package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.dto.shipping.ShippingAddressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuoteRequest {
    @NotNull(message = "postId is required")
    private UUID postId;

    @Valid
    private ShippingAddressRequest buyerAddress;

    private BigDecimal buyerLatitude;
    private BigDecimal buyerLongitude;
}

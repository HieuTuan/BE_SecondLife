package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.InspectionShippingQuoteResponse;
import com.secondlife.secondlife.dto.response.ShippingVehicleSelectionResponse;
import com.secondlife.secondlife.entity.InspectionShippingQuote;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class InspectionShippingQuoteMapper {
    private final Delegate delegate;

    public InspectionShippingQuoteResponse toResponse(InspectionShippingQuote row,
            InspectionShippingQuoteResponse.Leg outbound, InspectionShippingQuoteResponse.Leg returnReserve) {
        return delegate.toResponse(row, outbound, returnReserve);
    }

    public InspectionShippingQuoteResponse.Leg toLeg(String leg, BigDecimal shippingFee, String serviceType,
            String providerQuotationId, Instant expiresAt, ShippingVehicleSelectionResponse vehicleSelection) {
        return delegate.toLeg(leg, "LALAMOVE", shippingFee, serviceType, providerQuotationId, expiresAt, vehicleSelection);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "expiresAt", source = "row.expiresAt")
        InspectionShippingQuoteResponse toResponse(InspectionShippingQuote row,
                InspectionShippingQuoteResponse.Leg outbound, InspectionShippingQuoteResponse.Leg returnReserve);
        InspectionShippingQuoteResponse.Leg toLeg(String leg, String provider, BigDecimal shippingFee, String serviceType,
                String providerQuotationId, Instant expiresAt, ShippingVehicleSelectionResponse vehicleSelection);
    }
}

package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse.Settlement;
import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse.Shipping;
import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.Shipment;

import lombok.RequiredArgsConstructor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInspectionFundsMapper {
    private final Delegate delegate;

    public Settlement toSettlement(InspectionFeeSettlement settlement) {
        return delegate.toSettlement(settlement);
    }

    public Shipping toShipping(Shipment shipment) {
        return delegate.toShipping(shipment);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "centerRecipientUserId", source = "centerRecipientUser.id")
        Settlement toSettlement(InspectionFeeSettlement settlement);

        Shipping toShipping(Shipment shipment);
    }
}

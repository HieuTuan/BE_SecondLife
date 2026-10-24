package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.ShipmentResponse;
import com.secondlife.secondlife.entity.Shipment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class ShipmentMapper {
    private final Delegate delegate;

    public ShipmentMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ShipmentResponse toResponse(Shipment source) {
        return delegate.toResponse(source);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "shipmentId", source = "id")
        ShipmentResponse toResponse(Shipment source);
    }
}

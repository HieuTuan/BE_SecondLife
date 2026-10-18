package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.ShipmentEventResponse;
import com.secondlife.secondlife.entity.ShipmentEvent;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

@Component
public class ShipmentEventMapper {
    private final Delegate delegate;

    public ShipmentEventMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ShipmentEventResponse toResponse(ShipmentEvent source) {
        return delegate.toResponse(source);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        ShipmentEventResponse toResponse(ShipmentEvent source);
    }
}

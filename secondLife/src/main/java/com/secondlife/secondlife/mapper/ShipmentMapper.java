package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.ShipmentResponse;
import com.secondlife.secondlife.entity.Shipment;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

@Component
public class ShipmentMapper {
    private final Delegate delegate;

    public ShipmentMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ShipmentResponse toResponse(Shipment source) {
        return toResponse(source, false);
    }

    public ShipmentResponse toResponse(Shipment source, boolean canRebook) {
        return delegate.toResponse(source, canRebook);
    }

    public void snapshotRoute(Shipment shipment, ShippingAddress sender, ShippingAddress receiver) {
        delegate.snapshotRoute(shipment, sender, receiver);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "shipmentId", source = "source.id")
        @Mapping(target = "trackingUrl", source = "source.shareLink")
        @Mapping(target = "canRebook", source = "canRebook")
        ShipmentResponse toResponse(Shipment source, boolean canRebook);

        @BeanMapping(ignoreByDefault = true)
        @Mapping(target = "senderName", source = "sender.name")
        @Mapping(target = "senderPhone", source = "sender.phone")
        @Mapping(target = "senderAddress", source = "sender.address")
        @Mapping(target = "receiverName", source = "receiver.name")
        @Mapping(target = "receiverPhone", source = "receiver.phone")
        @Mapping(target = "receiverAddress", source = "receiver.address")
        void snapshotRoute(@MappingTarget Shipment shipment, ShippingAddress sender, ShippingAddress receiver);
    }
}

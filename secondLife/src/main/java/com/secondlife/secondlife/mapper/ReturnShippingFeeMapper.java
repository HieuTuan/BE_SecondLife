package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.ReturnShippingFeeResponse;
import com.secondlife.secondlife.entity.ReturnShippingFee;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class ReturnShippingFeeMapper {
    private final Delegate delegate;

    public ReturnShippingFeeMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ReturnShippingFeeResponse toResponse(ReturnShippingFee fee) {
        return delegate.toResponse(fee);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "shipmentId", source = "id")
        ReturnShippingFeeResponse toResponse(ReturnShippingFee fee);
    }
}

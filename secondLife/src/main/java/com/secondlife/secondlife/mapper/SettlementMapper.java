package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.SettlementResponse;
import com.secondlife.secondlife.entity.OrderSettlement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class SettlementMapper {
    private final Delegate delegate;

    public SettlementMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public SettlementResponse toResponse(OrderSettlement source) {
        return delegate.toResponse(source);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "settlementId", source = "id")
        SettlementResponse toResponse(OrderSettlement source);
    }
}

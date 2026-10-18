package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.VerificationEventResponse;
import com.secondlife.secondlife.entity.SellerVerificationEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class VerificationEventMapper {
    private final Delegate delegate;

    public VerificationEventMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public VerificationEventResponse toResponse(SellerVerificationEvent event) {
        return delegate.toResponse(event);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "actorUserId", source = "actorUser.id")
        VerificationEventResponse toResponse(SellerVerificationEvent event);
    }
}

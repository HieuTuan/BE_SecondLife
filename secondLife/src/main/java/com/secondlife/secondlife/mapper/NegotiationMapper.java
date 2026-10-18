package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.NegotiationResponseDTO;
import com.secondlife.secondlife.entity.Negotiation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class NegotiationMapper {
    private final Delegate delegate;

    public NegotiationMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public NegotiationResponseDTO toResponse(Negotiation negotiation) {
        return delegate.toResponse(negotiation);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "postId", source = "post.id")
        @Mapping(target = "postTitle", source = "post.title")
        @Mapping(target = "buyerId", source = "buyer.id")
        @Mapping(target = "sellerId", source = "post.user.id")
        NegotiationResponseDTO toResponse(Negotiation negotiation);
    }
}

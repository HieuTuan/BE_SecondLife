package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.ListingDraftResponse;
import com.secondlife.secondlife.entity.Post;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class ListingDraftMapper {
    private final Delegate delegate;

    public ListingDraftMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ListingDraftResponse toResponse(Post post) {
        return delegate.toResponse(post);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "postId", source = "id")
        ListingDraftResponse toResponse(Post post);
    }
}

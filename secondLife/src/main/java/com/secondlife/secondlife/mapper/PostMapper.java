package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import com.secondlife.secondlife.dto.response.PostDto;
import com.secondlife.secondlife.dto.response.PostDto.SellerSummary;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class PostMapper {
    private final Delegate delegate;

    public PostMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public MarketplaceListingResponse toMarketplaceResponse(Post post, boolean isInspected,
                                                            String tamperSealId, String conditionGrade,
                                                            String inspectionMethod) {
        return delegate.toMarketplaceResponse(post, isInspected, tamperSealId, conditionGrade, inspectionMethod);
    }

    public PostDto toResponse(Post post) {
        return delegate.toResponse(post);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "postId", source = "post.id")
        @Mapping(target = "sellerId", source = "post.user.id")
        @Mapping(target = "sellerName", source = "post.user.profile.fullName")
        @Mapping(target = "sellerAvatar", source = "post.user.profile.avatarUrl")
        @Mapping(target = "sellerAddress", source = "post.sellerPickupAddress")
        @Mapping(target = "isInspected", source = "isInspected")
        @Mapping(target = "tamperSealId", source = "tamperSealId")
        @Mapping(target = "conditionGrade", source = "conditionGrade")
        @Mapping(target = "inspectionMethod", source = "inspectionMethod")
        MarketplaceListingResponse toMarketplaceResponse(Post post, boolean isInspected,
                                                        String tamperSealId, String conditionGrade,
                                                        String inspectionMethod);

        @Mapping(target = "user", expression = "java(toSellerSummary(post.getUser(), post.getSellerPickupAddress()))")
        @Mapping(target = "sellerActions", ignore = true)
        PostDto toResponse(Post post);

        @Mapping(target = "fullName", source = "user.profile.fullName")
        @Mapping(target = "avatarUrl", source = "user.profile.avatarUrl")
        @Mapping(target = "address", source = "warehouseAddress")
        SellerSummary toSellerSummary(User user, String warehouseAddress);
    }
}

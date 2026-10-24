package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import com.secondlife.secondlife.dto.response.PostDto;
import com.secondlife.secondlife.dto.response.PostDto.SellerSummary;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PostMapper {
    private final Delegate delegate;

    public PostMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public MarketplaceListingResponse toMarketplaceResponse(Post post) {
        return delegate.toMarketplaceResponse(post);
    }

    public PostDto toResponse(Post post) {
        return delegate.toResponse(post);
    }

    protected SellerSummary toSellerSummary(User user) {
        return delegate.toSellerSummary(user);
    }

    public static String profileAddress(UserProfile profile) {
        if (profile == null) return null;
        List<String> parts = new ArrayList<>();
        if (profile.getStreetAddress() != null && !profile.getStreetAddress().isBlank()) parts.add(profile.getStreetAddress());
        if (profile.getWard() != null && !profile.getWard().isBlank()) parts.add(profile.getWard());
        if (profile.getDistrict() != null && !profile.getDistrict().isBlank()) parts.add(profile.getDistrict());
        if (profile.getProvince() != null && !profile.getProvince().isBlank()) parts.add(profile.getProvince());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "postId", source = "id")
        @Mapping(target = "sellerId", source = "user.id")
        @Mapping(target = "sellerName", source = "user.profile.fullName")
        @Mapping(target = "sellerAvatar", source = "user.profile.avatarUrl")
        @Mapping(target = "sellerAddress", expression = "java(profileAddress(post.getUser() == null ? null : post.getUser().getProfile()))")
        MarketplaceListingResponse toMarketplaceResponse(Post post);

        PostDto toResponse(Post post);

        @Mapping(target = "fullName", source = "profile.fullName")
        @Mapping(target = "avatarUrl", source = "profile.avatarUrl")
        @Mapping(target = "address", expression = "java(profileAddress(user.getProfile()))")
        SellerSummary toSellerSummary(User user);

        default String profileAddress(UserProfile profile) {
            return PostMapper.profileAddress(profile);
        }
    }
}

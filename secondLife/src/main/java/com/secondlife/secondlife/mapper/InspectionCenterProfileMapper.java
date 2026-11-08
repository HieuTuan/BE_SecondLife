package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.request.InspectionCenterProfileRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionCenterOptionResponse;
import com.secondlife.secondlife.dto.response.InspectionCenterProfileResponse;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;

import lombok.RequiredArgsConstructor;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class InspectionCenterProfileMapper {
    private final Delegate delegate;

    public void update(InspectionCenterProfileRequest request, UserProfile profile) {
        delegate.update(request, profile);
    }

    public InspectionCenterProfileRequest toRequest(UserProfile profile) {
        return delegate.toRequest(profile);
    }

    public ShippingAddress toShippingAddress(UserProfile profile) {
        return delegate.toShippingAddress(profile);
    }

    public InspectionCenterProfileResponse toResponse(User user, boolean configured) {
        return delegate.toResponse(user, configured);
    }

    public InspectionCenterOptionResponse toOption(User user, boolean configured) {
        InspectionCenterProfileResponse profile = toResponse(user, configured);
        return delegate.toOption(profile, profile.name() == null ? user.getEmail() : profile.name());
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        // This form edits center contact/location only; personal/account fields are preserved.
        @BeanMapping(ignoreByDefault = true)
        @Mapping(target = "fullName", source = "name")
        @Mapping(target = "phone", source = "phone")
        @Mapping(target = "province", source = "province")
        @Mapping(target = "district", source = "district")
        @Mapping(target = "ward", source = "ward")
        @Mapping(target = "streetAddress", source = "streetAddress")
        @Mapping(target = "latitude", source = "latitude")
        @Mapping(target = "longitude", source = "longitude")
        void update(InspectionCenterProfileRequest request, @MappingTarget UserProfile profile);

        @Mapping(target = "name", source = "fullName")
        InspectionCenterProfileRequest toRequest(UserProfile profile);

        @Mapping(target = "name", source = "fullName")
        @Mapping(target = "address", expression = "java(address(profile))")
        ShippingAddress toShippingAddress(UserProfile profile);

        @Mapping(target = "id", source = "user.id")
        @Mapping(target = "name", source = "user.profile.fullName")
        @Mapping(target = "phone", source = "user.profile.phone")
        @Mapping(target = "province", source = "user.profile.province")
        @Mapping(target = "district", source = "user.profile.district")
        @Mapping(target = "ward", source = "user.profile.ward")
        @Mapping(target = "streetAddress", source = "user.profile.streetAddress")
        @Mapping(target = "address", expression = "java(address(user.getProfile()))")
        @Mapping(target = "latitude", source = "user.profile.latitude")
        @Mapping(target = "longitude", source = "user.profile.longitude")
        @Mapping(target = "shippingAddressConfigured", source = "configured")
        @Mapping(target = "updatedAt", source = "user.profile.updatedAt")
        InspectionCenterProfileResponse toResponse(User user, boolean configured);

        @Mapping(target = "name", source = "name")
        InspectionCenterOptionResponse toOption(InspectionCenterProfileResponse profile, String name);

        default String trim(String value) {
            return value == null ? null : value.strip();
        }

        default BigDecimal coordinate(Double value) {
            return value == null || !Double.isFinite(value) ? null : BigDecimal.valueOf(value);
        }

        default String address(UserProfile profile) {
            if (profile == null) return null;
            String result = Stream.of(profile.getStreetAddress(), profile.getWard(), profile.getDistrict(), profile.getProvince())
                    .filter(Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty())
                    .collect(Collectors.joining(", "));
            return result.isEmpty() ? null : result;
        }
    }
}

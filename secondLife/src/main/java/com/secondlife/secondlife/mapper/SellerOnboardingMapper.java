package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.SellerOnboardingResponse;
import com.secondlife.secondlife.entity.SellerOnboarding;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class SellerOnboardingMapper {
    private final Delegate delegate;

    public SellerOnboardingMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public SellerOnboardingResponse toResponse(SellerOnboarding onboarding, ShippingAddress pickupAddress, boolean emailVerified, boolean canStartEkyc, String nextStep) {
        return delegate.toResponse(onboarding, pickupAddress, emailVerified, canStartEkyc, nextStep);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "shopName", source = "onboarding.shopName")
        @Mapping(target = "email", source = "onboarding.email")
        @Mapping(target = "phone", source = "onboarding.phone")
        @Mapping(target = "emailVerifiedAt", source = "onboarding.emailVerifiedAt")
        @Mapping(target = "pickupAddress", source = "pickupAddress")
        @Mapping(target = "emailVerified", source = "emailVerified")
        @Mapping(target = "canStartEkyc", source = "canStartEkyc")
        @Mapping(target = "nextStep", source = "nextStep")
        SellerOnboardingResponse toResponse(SellerOnboarding onboarding, ShippingAddress pickupAddress,
                boolean emailVerified, boolean canStartEkyc, String nextStep);
    }
}

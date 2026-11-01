package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.request.TopupPackageRequest;
import com.secondlife.secondlife.dto.response.TopupPackagePriceResponse;
import com.secondlife.secondlife.dto.response.TopupPackagePurchaseResponse;
import com.secondlife.secondlife.dto.response.TopupPackageResponse;
import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.entity.TopupPackagePurchase;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

@Component
public class TopupPackageMapper {
    private final Delegate delegate;

    public TopupPackageMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public TopupPackageResponse toResponse(TopupPackage source, TopupPackagePriceResponse pricing) {
        return delegate.toResponse(source, pricing);
    }

    public TopupPackagePurchaseResponse toPurchaseResponse(TopupPackagePurchase source) {
        return delegate.toPurchaseResponse(source);
    }
    public TopupPackage toEntity(TopupPackageRequest request) {
        return delegate.toEntity(request);
    }

    public void updateEntity(TopupPackageRequest request, TopupPackage target) {
        delegate.updateEntity(request, target);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "discountPercentage", source = "pricing.discountPercentage")
        @Mapping(target = "referencePrice", source = "pricing.referencePrice")
        @Mapping(target = "pricingAvailable", source = "pricing.pricingAvailable")
        @Mapping(target = "packageVersion", source = "source.version")
        TopupPackageResponse toResponse(TopupPackage source, TopupPackagePriceResponse pricing);

        TopupPackagePurchaseResponse toPurchaseResponse(TopupPackagePurchase source);

        @Mapping(target = "id", ignore = true)
        @Mapping(target = "version", ignore = true)
        @Mapping(target = "discountPercentage", ignore = true)
        TopupPackage toEntity(TopupPackageRequest request);

        @Mapping(target = "id", ignore = true)
        @Mapping(target = "version", ignore = true)
        @Mapping(target = "discountPercentage", ignore = true)
        void updateEntity(TopupPackageRequest request, @MappingTarget TopupPackage target);
    }
}

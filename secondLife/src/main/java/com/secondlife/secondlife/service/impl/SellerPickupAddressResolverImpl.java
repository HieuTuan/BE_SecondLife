package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.SellerPickupAddressRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.SellerPickupAddressResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("sellerPickupAddressResolver")
@RequiredArgsConstructor
public class SellerPickupAddressResolverImpl implements SellerPickupAddressResolver {
    private final ReverseGeocodingService geocoding;
    public ShippingAddress resolve(SellerPickupAddressRequest request) {
        if (request.latitude() == null || request.longitude() == null) {
            throw new BadRequestException("Nhập vĩ độ và kinh độ của địa chỉ lấy hàng");
        }
        if (request.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || request.latitude().compareTo(BigDecimal.valueOf(90)) > 0
                || request.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || request.longitude().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BadRequestException("Pickup coordinates are outside the valid geographical ranges");
        }
        return geocoding.shipping(new ShippingAddressRequest(
                request.name(), request.phone(), request.latitude(), request.longitude()));
    }
}

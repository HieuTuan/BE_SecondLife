package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.dto.request.SellerPickupAddressRequest;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import com.secondlife.secondlife.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SellerPickupAddressResolver {
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
        return geocoding.shipping(new com.secondlife.secondlife.dto.shipping.ShippingAddressRequest(
                request.name(), request.phone(), request.latitude(), request.longitude()));
    }
}

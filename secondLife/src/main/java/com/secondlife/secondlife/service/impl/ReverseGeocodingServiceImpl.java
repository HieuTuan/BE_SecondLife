package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.GeocodedAddressResponse;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.integration.geocoding.GeoapifyGeocodingClient;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service("reverseGeocodingService")
@RequiredArgsConstructor
public class ReverseGeocodingServiceImpl implements ReverseGeocodingService {
    private final GeoapifyGeocodingClient geocoding;

    @Override
    public ShippingAddress shipping(ShippingAddressRequest request) { return geocoding.shipping(request); }

    @Override
    public GeocodedAddressResponse reverse(BigDecimal latitude, BigDecimal longitude) {
        return geocoding.reverse(latitude, longitude);
    }
}

package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.GeocodedAddressResponse;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import java.math.BigDecimal;

public interface ReverseGeocodingService {
    ShippingAddress shipping(ShippingAddressRequest request);
    GeocodedAddressResponse reverse(BigDecimal latitude, BigDecimal longitude);
}

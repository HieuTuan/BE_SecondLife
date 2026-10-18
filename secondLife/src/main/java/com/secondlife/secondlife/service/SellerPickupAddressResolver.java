package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SellerPickupAddressRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;

public interface SellerPickupAddressResolver {
    ShippingAddress resolve(SellerPickupAddressRequest request);

}

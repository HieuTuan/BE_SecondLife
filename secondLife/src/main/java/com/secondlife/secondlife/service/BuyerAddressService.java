package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.BuyerDeliveryAddressRequest;
import com.secondlife.secondlife.dto.response.BuyerDeliveryAddressResponse;

import java.util.List;
import java.util.UUID;

public interface BuyerAddressService {

    List<BuyerDeliveryAddressResponse> getAddresses(UUID userId);

    BuyerDeliveryAddressResponse addAddress(UUID userId, BuyerDeliveryAddressRequest request);

    BuyerDeliveryAddressResponse updateAddress(UUID userId, String addressId, BuyerDeliveryAddressRequest request);

    void deleteAddress(UUID userId, String addressId);

    BuyerDeliveryAddressResponse setDefaultAddress(UUID userId, String addressId);

    List<BuyerDeliveryAddressResponse> syncAddresses(UUID userId, List<BuyerDeliveryAddressRequest> requests);
}

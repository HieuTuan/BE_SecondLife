package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionCenterProfileRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionCenterOptionResponse;
import com.secondlife.secondlife.dto.response.InspectionCenterProfileResponse;

import java.util.List;
import java.util.UUID;

public interface InspectionCenterProfileService {
    InspectionCenterProfileResponse getMyProfile(UUID actorId);
    InspectionCenterProfileResponse updateMyProfile(UUID actorId, InspectionCenterProfileRequest request);
    List<InspectionCenterOptionResponse> getAvailableCenters();
    ShippingAddress requireShippingAddress(UUID centerId);
}

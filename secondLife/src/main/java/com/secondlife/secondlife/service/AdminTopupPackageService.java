package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.TopupPackageRequest;
import com.secondlife.secondlife.dto.response.TopupPackageResponse;

import java.util.List;
import java.util.UUID;

public interface AdminTopupPackageService {
    List<TopupPackageResponse> getPackages();

    TopupPackageResponse getPackage(UUID packageId);

    TopupPackageResponse createPackage(TopupPackageRequest request);

    TopupPackageResponse updatePackage(UUID packageId, TopupPackageRequest request);

    void deletePackage(UUID packageId);

    TopupPackageResponse setActive(UUID packageId, boolean active);
}

package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.TopupPackageResponse;
import java.util.List;

public interface TopupPackageQueryService {
    List<TopupPackageResponse> getPackages();
}

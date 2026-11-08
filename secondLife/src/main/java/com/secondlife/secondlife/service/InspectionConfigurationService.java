package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.InspectionConfigurationResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;

import java.util.UUID;

public interface InspectionConfigurationService {
    InspectionConfigurationResponse resolve(Post post, UUID inspectionCenterId);
    void snapshot(InspectionOrder order, InspectionConfigurationResponse configuration);
}

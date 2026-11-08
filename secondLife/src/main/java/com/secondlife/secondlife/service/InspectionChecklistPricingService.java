package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.InspectionChecklistPricingResponse;
import com.secondlife.secondlife.entity.InspectionChecklistTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InspectionChecklistPricingService {
    Map<UUID, InspectionChecklistPricingResponse> getPrices(List<InspectionChecklistTemplate> templates);
}

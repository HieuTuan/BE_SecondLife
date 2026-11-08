package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ChecklistItemResultDto;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.entity.InspectionOrder;

import java.util.List;
import java.util.UUID;

public interface InspectionOrderChecklistService {
    InspectionChecklistResponse getForOrder(UUID actorId, UUID orderId);
    List<ChecklistItemResultDto> validateResults(InspectionOrder order, List<ChecklistItemResultDto> results, String outcome);
}

package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import java.util.UUID;

public interface InspectionOrderChecklistService {
    InspectionChecklistResponse getForOrder(UUID orderId);
}

package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.ShipmentEventResponse;
import java.util.List;
import java.util.UUID;

public interface ShipmentTimelineService {
    List<ShipmentEventResponse> getTimeline(UUID actorId, boolean staff, UUID shipmentId);
}

package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.ShipmentEventResponse;
import com.secondlife.secondlife.mapper.ShipmentEventMapper;
import com.secondlife.secondlife.service.ShipmentTimelineService;
import com.secondlife.secondlife.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipmentTimelineServiceImpl implements ShipmentTimelineService {

    private final ShipmentService shipmentService;
    private final ShipmentEventMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<ShipmentEventResponse> getTimeline(UUID actorId, boolean staff, UUID shipmentId) {
        return shipmentService.timeline(actorId, staff, shipmentId).stream().map(mapper::toResponse).toList();
    }
}

package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.CreateShipmentRequest;
import com.secondlife.secondlife.dto.request.ReportRequests;
import com.secondlife.secondlife.dto.response.ShipmentResponse;
import com.secondlife.secondlife.entity.ShipmentEvent;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;

public interface ShipmentService {
    ShipmentResponse createForOrder(UUID actor, boolean staff, UUID orderId, CreateShipmentRequest request);

    ShipmentResponse createForInspection(UUID actor, boolean staff, UUID inspectionId, CreateShipmentRequest request);

    List<ShipmentResponse> orderShipments(UUID actor, boolean staff, UUID id);

    List<ShipmentResponse> inspectionShipments(UUID actor, boolean staff, UUID id);

    List<ShipmentEvent> timeline(UUID actor, boolean staff, UUID id);

    ShipmentResponse cancel(UUID actor, boolean staff, UUID id);

    ShipmentResponse refundReturned(UUID actor, boolean staff, UUID id, String reason);

    ShipmentResponse refundReturned(UUID actor, boolean staff, UUID id, String reason, ReportRequests.ResponsibleParty responsibleParty);

    ShipmentResponse sync(UUID actor, boolean staff, UUID id);

    void receiveLalamove(JsonNode body);

    ShipmentResponse reconcileCreation(UUID actor, boolean staff, UUID id, String code);

    JsonNode tracking(UUID actor, boolean staff, UUID id);

}

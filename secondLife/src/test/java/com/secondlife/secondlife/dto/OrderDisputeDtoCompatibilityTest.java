package com.secondlife.secondlife.dto;

import com.secondlife.secondlife.dto.request.CreateShipmentRequest;
import com.secondlife.secondlife.dto.request.ReportRequests.Decision;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.Resolve;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderDisputeDtoCompatibilityTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void legacyResolveConstructorDefaultsToNoReturnAction() {
        var request = new Resolve(Decision.CONFIRMED, ResponsibleParty.SELLER, "Description confirmed");
        var payload = json.readTree(json.writeValueAsString(request));

        assertEquals("NONE", payload.path("resolutionAction").asString());
    }

    @Test
    void omittedAndExplicitNullActionsDefaultToNoReturnAction() {
        for (String payload : new String[]{
                "{\"decision\":\"CONFIRMED\",\"responsibleParty\":\"SELLER\",\"reason\":\"Confirmed\"}",
                "{\"decision\":\"CONFIRMED\",\"responsibleParty\":\"SELLER\",\"reason\":\"Confirmed\",\"resolutionAction\":null}"
        }) {
            var request = json.readValue(payload, Resolve.class);
            assertEquals(ResolutionAction.NONE, request.resolutionAction());
        }
    }

    @Test
    void explicitReturnActionSurvivesJsonBinding() {
        var request = json.readValue("""
                {"decision":"CONFIRMED","responsibleParty":"SELLER","reason":"Return approved",
                 "resolutionAction":"RETURN_AND_REFUND"}
                """, Resolve.class);

        assertEquals(ResolutionAction.RETURN_AND_REFUND, request.resolutionAction());
        assertEquals("RETURN_AND_REFUND", json.readTree(json.writeValueAsString(request)).get("resolutionAction").asString());
    }

    @Test
    void shipmentRequestSupportsLegacyConstructorAndReportLink() {
        UUID requestId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        var legacy = new CreateShipmentRequest(requestId, "SELLER_TO_BUYER", null, null, null);
        var request = json.readValue("""
                {"requestId":"%s","leg":"BUYER_TO_SELLER","reason":"Return approved","reportId":"%s"}
                """.formatted(requestId, reportId), CreateShipmentRequest.class);

        assertNull(legacy.reportId());
        assertEquals(requestId, request.requestId());
        assertEquals(reportId, request.reportId());
        assertEquals(reportId.toString(), json.readTree(json.writeValueAsString(request)).get("reportId").asString());
    }
}

package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.integration.shipping.LalamoveWebhookVerifier;
import com.secondlife.secondlife.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequiredArgsConstructor
public class LalamoveWebhookController {
    private final LalamoveWebhookVerifier verifier;
    private final ShipmentService shipments;

    @PostMapping(LalamoveWebhookVerifier.CALLBACK_PATH)
    @Operation(summary = "Receive signed Lalamove v3 events", description = "Carrier webhook, not a STAFF status editing API. An empty setup probe returns 200 without changing shipments.")
    public ApiResponse<Void> callback(@RequestBody(required = false) String body,
                                     @RequestHeader(value = "Content-Type", required = false) String contentType) {
        if (body != null && !body.isBlank()) {
            try {
                if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType)))
                    throw new BadRequestException("Lalamove webhook Content-Type must be application/json");
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Lalamove webhook Content-Type must be application/json");
            }
        }
        JsonNode event = verifier.verifyAndNormalize(body);
        if (event != null) shipments.receiveLalamove(event);
        return ApiResponse.success((Void) null);
    }
}

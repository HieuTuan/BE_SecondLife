package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.service.CreditPaymentCallbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "PAYMENT_MOCK_CALLBACK_ENABLED", havingValue = "true")
@Tag(name = "Payment Callbacks", description = "Signed mock payment callback for local integration")
public class MockPaymentCallbackController {
    private final CreditPaymentCallbackService callbackService;

    @PostMapping("/api/payment-callbacks/mock")
    @Operation(summary = "Accept a signed mock payment status event")
    public ResponseEntity<ApiResponse<Void>> accept(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Mock-Signature", required = false) String signature) {
        callbackService.processMockCallback(rawBody, signature);
        return ResponseEntity.ok(ApiResponse.success("Payment callback accepted"));
    }
}

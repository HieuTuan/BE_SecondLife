package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.SePayWebhookDTO;
import com.secondlife.secondlife.service.DepositService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks/sepay")
@RequiredArgsConstructor
@Slf4j
public class SePayWebhookController {

    private final DepositService depositService;

    @org.springframework.beans.factory.annotation.Value("${app.sepay.api-token}")
    private String sepayApiToken;

    @PostMapping
    public ResponseEntity<Map<String, Boolean>> handleWebhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-SePay-Signature", required = false) String signature,
            @RequestBody SePayWebhookDTO webhookDTO) {
            
        // Check API Token robustly
        String expectedToken = sepayApiToken.trim();
        if (authorization == null) {
            log.warn("Missing SePay API Token (Authorization header is null)");
            return ResponseEntity.status(401).body(Map.of("success", false));
        }
        
        String receivedToken = authorization.replace("Bearer ", "")
                                            .replace("Apikey ", "")
                                            .replace("Bearer", "")
                                            .replace("Apikey", "")
                                            .trim();
        
        if (expectedToken.isBlank() || !java.security.MessageDigest.isEqual(receivedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), expectedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            log.warn("Invalid SePay webhook authorization");
            return ResponseEntity.status(401).body(Map.of("success", false));
        }
            
        log.info("Received SePay webhook for transaction ID: {}", webhookDTO.getId());
        
        try {
            depositService.processSepayWebhook(webhookDTO);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Error processing SePay webhook: ", e);
            // Must still return 200/201 success: true format so SePay stops retrying
            // Alternatively, return error if we want SePay to retry
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
    }
}

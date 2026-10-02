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

    // SePay supports passing a static API key in headers to secure the webhook.
    // For now we assume network security or you can add a simple token check here.
    @PostMapping
    public ResponseEntity<Map<String, Boolean>> handleWebhook(@RequestBody SePayWebhookDTO webhookDTO) {
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

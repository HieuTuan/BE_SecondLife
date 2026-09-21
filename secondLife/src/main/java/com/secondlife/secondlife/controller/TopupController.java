package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.entity.UserCredit;
import com.secondlife.secondlife.service.CreditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/topup")
public class TopupController {

    private final CreditService creditService;

    public TopupController(CreditService creditService) {
        this.creditService = creditService;
    }

    @GetMapping("/packages")
    public ResponseEntity<List<TopupPackage>> getPackages() {
        return ResponseEntity.ok(creditService.getAllTopupPackages());
    }

    @GetMapping("/my-credit")
    public ResponseEntity<UserCredit> getMyCredit(@RequestAttribute("userId") UUID userId) {
        // Assuming userId is populated in RequestAttribute by auth filter
        return ResponseEntity.ok(creditService.getUserCredit(userId));
    }

    @PostMapping("/purchase/{packageId}")
    public ResponseEntity<UserCredit> purchasePackage(
            @RequestAttribute("userId") UUID userId,
            @PathVariable UUID packageId) {
        return ResponseEntity.ok(creditService.purchaseTopupPackage(userId, packageId));
    }
}

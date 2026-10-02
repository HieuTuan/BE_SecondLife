package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WalletResponseDTO> getMyWallet(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        UserWallet wallet = walletService.getWalletByUserId(userId);
        
        WalletResponseDTO dto = new WalletResponseDTO();
        dto.setId(wallet.getId());
        dto.setUserId(wallet.getUser().getId());
        dto.setBalance(wallet.getBalance());
        dto.setUpdatedAt(wallet.getUpdatedAt());
        
        return ResponseEntity.ok(dto);
    }

    // A simple endpoint for testing/depositing money to one's own wallet
    @PostMapping("/me/deposit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> depositMoney(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam BigDecimal amount) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        walletService.deposit(userId, amount, UUID.randomUUID());
        return ResponseEntity.ok("Deposited successfully");
    }
}

package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.DepositService;
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
    private final DepositService depositService;
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

    @PostMapping("/deposit-request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.secondlife.secondlife.dto.response.DepositResponseDTO> createDepositRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @jakarta.validation.Valid @RequestBody com.secondlife.secondlife.dto.request.DepositCreateRequestDTO requestDTO) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(depositService.createDepositRequest(userId, requestDTO));
    }

    @GetMapping("/me/transactions")
    @PreAuthorize("isAuthenticated()")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get wallet transaction history")
    public ResponseEntity<com.secondlife.secondlife.common.PageResponse<com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO>> getMyTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @org.springdoc.core.annotations.ParameterObject org.springframework.data.domain.Pageable pageable) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        org.springframework.data.domain.Page<com.secondlife.secondlife.entity.WalletTransaction> page = walletService.getTransactions(userId, pageable);
        
        org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO> dtoPage = page.map(t -> com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO.builder()
                .id(t.getId())
                .walletId(t.getWallet().getId())
                .amount(t.getAmount())
                .type(t.getType())
                .referenceId(t.getReferenceId())
                .createdAt(t.getCreatedAt())
                .build());
                
        return ResponseEntity.ok(com.secondlife.secondlife.common.PageResponse.from(dtoPage));
    }
}

package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.NegotiationRequestDTO;
import com.secondlife.secondlife.dto.response.NegotiationResponseDTO;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.NegotiationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/negotiations")
@RequiredArgsConstructor
public class NegotiationController {

    private final NegotiationService negotiationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NegotiationResponseDTO> createNegotiation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody NegotiationRequestDTO requestDTO) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(negotiationService.createNegotiation(buyerId, requestDTO));
    }

    @GetMapping("/buyer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NegotiationResponseDTO>> getBuyerNegotiations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(negotiationService.getBuyerNegotiations(buyerId, pageable));
    }

    @GetMapping("/seller")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NegotiationResponseDTO>> getSellerNegotiations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        UUID sellerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(negotiationService.getSellerNegotiations(sellerId, pageable));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NegotiationResponseDTO> acceptNegotiation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID sellerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(negotiationService.acceptNegotiation(sellerId, id));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NegotiationResponseDTO> rejectNegotiation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID sellerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(negotiationService.rejectNegotiation(sellerId, id));
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NegotiationResponseDTO> cancelNegotiation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id) {
        UUID buyerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(negotiationService.cancelNegotiation(buyerId, id));
    }
}

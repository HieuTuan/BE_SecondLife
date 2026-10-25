package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.DepositCreateRequestDTO;
import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.response.DepositResponseDTO;
import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.enums.WalletTransactionType;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.DepositService;
import com.secondlife.secondlife.service.WalletQueryService;
import com.secondlife.secondlife.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;
    private final WalletQueryService walletQueryService;
    private final DepositService depositService;
    private final CurrentUserProvider currentUserProvider;

    public WalletController(WalletService walletService,
                            DepositService depositService,
                            WalletQueryService walletQueryService,
                            CurrentUserProvider currentUserProvider) {
        this.walletService = walletService;
        this.walletQueryService = walletQueryService;
        this.depositService = depositService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WalletResponseDTO> getMyWallet(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(walletQueryService.getWallet(userId));
    }

    @PostMapping("/deposit-request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DepositResponseDTO> createDepositRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DepositCreateRequestDTO requestDTO) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(depositService.createDepositRequest(userId, requestDTO));
    }

    @GetMapping("/me/transactions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get own wallet transaction history",
            description = "Filter by type and UTC instants: from is inclusive, to is exclusive. Default sort: createdAt DESC, id DESC.")
    public ResponseEntity<PageResponse<WalletTransactionResponseDTO>> getMyTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) WalletTransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject Pageable pageable) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(PageResponse.from(
                walletQueryService.getTransactions(userId, type, from, to, pageable)));
    }

    @GetMapping("/me/transactions/{transactionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get details of one own wallet transaction",
            description = "Unknown and foreign-owned transaction IDs both return 404. Legacy balances can be null.")
    public ResponseEntity<WalletTransactionResponseDTO> getMyTransaction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID transactionId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(walletQueryService.getTransaction(userId, transactionId));
    }

    @PostMapping("/withdrawals")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Yêu cầu rút tiền từ ví về tài khoản ngân hàng")
    public ResponseEntity<WalletWithdrawalResponse> requestWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody WalletWithdrawalRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(walletService.requestWithdrawal(userId, request));
    }

    @GetMapping("/withdrawals")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử yêu cầu rút tiền của người dùng")
    public ResponseEntity<PageResponse<WalletWithdrawalResponse>> getMyWithdrawals(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject Pageable pageable) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(PageResponse.from(walletService.getMyWithdrawals(userId, pageable)));
    }

    @GetMapping("/admin/withdrawals")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN_RBAC_MANAGE')")
    @Operation(summary = "Admin xem danh sách yêu cầu rút tiền")
    public ResponseEntity<PageResponse<WalletWithdrawalResponse>> getAllWithdrawals(
            @RequestParam(required = false) String status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(walletService.getAllWithdrawals(status, pageable)));
    }

    @PostMapping("/admin/withdrawals/{id}/review")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ADMIN_RBAC_MANAGE')")
    @Operation(summary = "Admin phê duyệt hoặc từ chối yêu cầu rút tiền")
    public ResponseEntity<WalletWithdrawalResponse> reviewWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody WalletWithdrawalReviewRequest request) {
        UUID adminId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(walletService.reviewWithdrawal(adminId, id, request));
    }
}

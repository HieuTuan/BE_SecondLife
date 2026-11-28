package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.DepositCreateRequestDTO;
import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalDisputeRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalExceptionRequest;
import com.secondlife.secondlife.dto.response.DepositResponseDTO;
import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.dto.response.WithdrawalBankConfirmationResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private static final String DEFAULT_BANK_WARNING_TEXT =
            "Vui lòng kiểm tra kỹ tên ngân hàng, số tài khoản và tên chủ tài khoản trước khi xác nhận. SecondLife sẽ thực hiện chuyển khoản theo thông tin bạn cung cấp. Nếu thông tin không chính xác, tiền có thể được chuyển nhầm hoặc khó thu hồi. Bạn chịu trách nhiệm về tính chính xác của thông tin đã xác nhận theo các điều khoản và quy định áp dụng.";

    private final WalletService walletService;
    private final WalletQueryService walletQueryService;
    private final DepositService depositService;
    private final CurrentUserProvider currentUserProvider;
    @Value("${app.wallet.withdrawal.bank-warning.version:withdrawal-bank-warning-v1}")
    private String bankWarningVersion = "withdrawal-bank-warning-v1";
    @Value("${app.wallet.withdrawal.bank-warning.text:}")
    private String bankWarningText = "";

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
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<WalletResponseDTO> getMyWallet(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(walletQueryService.getWallet(userId));
    }

    @PostMapping("/deposit-request")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<DepositResponseDTO> createDepositRequest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DepositCreateRequestDTO requestDTO) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(depositService.createDepositRequest(userId, requestDTO));
    }

    @GetMapping("/deposit-request/{requestId}")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<DepositResponseDTO> getDepositRequest(
            @AuthenticationPrincipal CustomUserDetails details, @PathVariable UUID requestId) {
        return ResponseEntity
                .ok(depositService.getDepositRequest(currentUserProvider.resolveUserId(details), requestId));
    }

    @GetMapping("/me/transactions")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    @Operation(summary = "Get own wallet transaction history", description = "Filter by type and UTC instants: from is inclusive, to is exclusive. Default sort: createdAt DESC, id DESC.")
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
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    @Operation(summary = "Get details of one own wallet transaction", description = "Unknown and foreign-owned transaction IDs both return 404. Legacy balances can be null.")
    public ResponseEntity<WalletTransactionResponseDTO> getMyTransaction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID transactionId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(walletQueryService.getTransaction(userId, transactionId));
    }

    @PostMapping("/withdrawals")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    @Operation(summary = "Yêu cầu rút tiền từ ví về tài khoản ngân hàng")
    public ResponseEntity<WalletWithdrawalResponse> requestWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody WalletWithdrawalRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(name = "X-Bank-Info-Confirmed", required = false, defaultValue = "false") boolean bankInfoConfirmed,
            @RequestHeader(name = "X-Bank-Info-Warning-Version", required = false) String warningVersion) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(
                walletService.requestWithdrawal(userId, request, idempotencyKey, bankInfoConfirmed, warningVersion));
    }

    @GetMapping("/withdrawal-bank-confirmation")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<WithdrawalBankConfirmationResponse> getBankConfirmationWarning() {
        String effectiveText = (bankWarningText != null && !bankWarningText.isBlank())
                ? bankWarningText.trim()
                : DEFAULT_BANK_WARNING_TEXT;
        return ResponseEntity.ok(new WithdrawalBankConfirmationResponse(bankWarningVersion, effectiveText));
    }

    @GetMapping("/withdrawals")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    @Operation(summary = "Lịch sử yêu cầu rút tiền của người dùng")
    public ResponseEntity<PageResponse<WalletWithdrawalResponse>> getMyWithdrawals(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ParameterObject Pageable pageable) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(PageResponse.from(walletService.getMyWithdrawals(userId, pageable)));
    }

    @GetMapping("/withdrawals/{id}")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<WalletWithdrawalResponse> getMyWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID id) {
        return ResponseEntity.ok(walletService.getMyWithdrawal(currentUserProvider.resolveUserId(userDetails), id));
    }

    @GetMapping("/withdrawals/{id}/history")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<java.util.List<com.secondlife.secondlife.dto.response.WalletWithdrawalStatusHistoryResponse>> getMyWithdrawalHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID id) {
        return ResponseEntity
                .ok(walletService.getMyWithdrawalStatusHistory(currentUserProvider.resolveUserId(userDetails), id));
    }

    @PostMapping("/withdrawals/{id}/disputes")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<WalletWithdrawalResponse> reportWithdrawalIssue(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID id,
            @Valid @RequestBody WalletWithdrawalDisputeRequest request) {
        return ResponseEntity.ok(walletService.reportWithdrawalIssue(
                currentUserProvider.resolveUserId(userDetails),
                id,
                request.type(),
                request.description(),
                request.evidenceUrls()));
    }

    @PostMapping("/withdrawals/{id}/exception")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    @Operation(summary = "Gửi yêu cầu hỗ trợ ngoại lệ sau khi giao dịch rút tiền đã hoàn tất")
    public ResponseEntity<WalletWithdrawalResponse> requestWithdrawalException(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody WalletWithdrawalExceptionRequest request) {
        return ResponseEntity.ok(walletService.requestWithdrawalException(
                currentUserProvider.resolveUserId(userDetails),
                id,
                request.reason(),
                request.evidenceUrls()));
    }

    @PostMapping("/withdrawals/{id}/received")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<WalletWithdrawalResponse> confirmReceipt(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID id) {
        return ResponseEntity.ok(walletService.confirmReceipt(currentUserProvider.resolveUserId(userDetails), id));
    }

    @PostMapping("/withdrawals/{id}/cancel")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<WalletWithdrawalResponse> cancelWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID id) {
        return ResponseEntity
                .ok(walletService.cancelWithdrawal(currentUserProvider.resolveUserId(userDetails), id, null));
    }

    @PostMapping(value = "/admin/withdrawals/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
    public ResponseEntity<WalletWithdrawalResponse> uploadWithdrawalEvidence(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {
        UUID staffId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(walletService.uploadWithdrawalEvidence(staffId, id, file));
    }

    @GetMapping("/withdrawals/evidence/{id}")
    @PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
    public ResponseEntity<byte[]> getWithdrawalEvidence(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID id,
            org.springframework.security.core.Authentication authentication) {
        var authorities = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        boolean staff = (authorities.contains("ROLE_STAFF") && authorities.contains("STAFF_DISPUTE_REVIEW"))
                || (authorities.contains("ROLE_ADMIN") && authorities.contains("ADMIN_RBAC_MANAGE"));
        var content = walletService.readWithdrawalEvidence(currentUserProvider.resolveUserId(userDetails), staff, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType())).body(content.bytes());
    }

    @GetMapping("/admin/withdrawals")
    @PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
    @Operation(summary = "Admin xem danh sách yêu cầu rút tiền")
    public ResponseEntity<PageResponse<WalletWithdrawalResponse>> getAllWithdrawals(
            @RequestParam(required = false) String status,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(walletService.getAllWithdrawals(status, pageable)));
    }

    @GetMapping("/admin/withdrawals/{id}/history")
    @PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
    @Operation(summary = "Admin/Staff xem lịch sử trạng thái của yêu cầu rút tiền")
    public ResponseEntity<java.util.List<com.secondlife.secondlife.dto.response.WalletWithdrawalStatusHistoryResponse>> getAdminWithdrawalHistory(
            @PathVariable UUID id) {
        return ResponseEntity.ok(walletService.getAdminWithdrawalStatusHistory(id));
    }

    @PostMapping("/admin/withdrawals/{id}/review")
    @PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
    @Operation(summary = "Admin phê duyệt hoặc từ chối yêu cầu rút tiền")
    public ResponseEntity<WalletWithdrawalResponse> reviewWithdrawal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody WalletWithdrawalReviewRequest request) {
        UUID adminId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(walletService.reviewWithdrawal(adminId, id, request));
    }
}

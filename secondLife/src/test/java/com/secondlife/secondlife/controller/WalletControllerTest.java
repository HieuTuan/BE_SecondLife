package com.secondlife.secondlife.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.DepositService;
import com.secondlife.secondlife.service.WalletService;
import com.secondlife.secondlife.service.WalletQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private WalletQueryService walletQueryService;

    @Mock
    private DepositService depositService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private WalletController walletController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private final UUID userId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        walletController = new WalletController(walletService, depositService, walletQueryService, currentUserProvider);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(walletController)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMyWalletKeepsTheWalletResponseShape() throws Exception {
        UUID walletId = UUID.randomUUID();
        var response = new com.secondlife.secondlife.dto.response.WalletResponseDTO(
                walletId,
                userId,
                new BigDecimal("125000.00"),
                Instant.parse("2026-01-01T00:00:00Z")
        );
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);
        when(walletQueryService.getWallet(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/wallets/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(walletId.toString()))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.balance").value(125000))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void getMyTransactionsKeepsThePageMetadataAndResponseFields() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        var response = com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO.builder()
                .id(transactionId)
                .walletId(walletId)
                .amount(new BigDecimal("50000.00"))
                .referenceId(UUID.randomUUID())
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);
        when(walletQueryService.getTransactions(eq(userId), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response), PageRequest.of(1, 10), 35));

        mockMvc.perform(get("/api/v1/wallets/me/transactions").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(transactionId.toString()))
                .andExpect(jsonPath("$.items[0].walletId").value(walletId.toString()))
                .andExpect(jsonPath("$.items[0].amount").value(50000))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(35))
                .andExpect(jsonPath("$.totalPages").value(4));
    }

    @Test
    @DisplayName("POST /api/v1/wallets/withdrawals - Yêu cầu rút tiền thành công")
    void requestWithdrawal_Success() throws Exception {
        WalletWithdrawalRequest request = new WalletWithdrawalRequest(
                new BigDecimal("150000"),
                "VCB",
                "0123456789",
                "NGUYEN VAN A"
        );

        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawalResponse response = WalletWithdrawalResponse.builder()
                .id(withdrawalId)
                .userId(userId)
                .userEmail("user@example.com")
                .amount(new BigDecimal("150000"))
                .bankCode("VCB")
                .accountNumber("0123456789")
                .accountHolderName("NGUYEN VAN A")
                .status("PENDING_REVIEW")
                .createdAt(Instant.now())
                .build();

        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);
        when(walletService.requestWithdrawal(eq(userId), any(WalletWithdrawalRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/wallets/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(withdrawalId.toString()))
                .andExpect(jsonPath("$.amount").value(150000))
                .andExpect(jsonPath("$.bankCode").value("VCB"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    @DisplayName("POST /api/v1/wallets/withdrawals - Validate số tiền nhỏ hơn 50,000 VND")
    void requestWithdrawal_AmountTooLow_ThrowsValidationError() throws Exception {
        WalletWithdrawalRequest request = new WalletWithdrawalRequest(
                new BigDecimal("20000"), // Under 50,000
                "VCB",
                "0123456789",
                "NGUYEN VAN A"
        );

        mockMvc.perform(post("/api/v1/wallets/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/wallets/withdrawals - Lịch sử rút tiền của người dùng")
    void getMyWithdrawals_Success() throws Exception {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawalResponse item = WalletWithdrawalResponse.builder()
                .id(withdrawalId)
                .userId(userId)
                .amount(new BigDecimal("200000"))
                .bankCode("MB")
                .status("APPROVED")
                .createdAt(Instant.now())
                .build();

        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);
        when(walletService.getMyWithdrawals(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/wallets/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(withdrawalId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/v1/wallets/admin/withdrawals - Admin xem danh sách rút tiền")
    void getAllWithdrawals_Admin_Success() throws Exception {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawalResponse item = WalletWithdrawalResponse.builder()
                .id(withdrawalId)
                .userId(userId)
                .amount(new BigDecimal("500000"))
                .bankCode("TCB")
                .status("PENDING_REVIEW")
                .createdAt(Instant.now())
                .build();

        when(walletService.getAllWithdrawals(eq("PENDING_REVIEW"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/wallets/admin/withdrawals")
                        .param("status", "PENDING_REVIEW")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(withdrawalId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING_REVIEW"));
    }

    @Test
    @DisplayName("POST /api/v1/wallets/admin/withdrawals/{id}/review - Admin duyệt yêu cầu")
    void reviewWithdrawal_Admin_Success() throws Exception {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawalReviewRequest reviewRequest = new WalletWithdrawalReviewRequest("APPROVE", "Duyệt thành công");

        WalletWithdrawalResponse response = WalletWithdrawalResponse.builder()
                .id(withdrawalId)
                .userId(userId)
                .amount(new BigDecimal("500000"))
                .status("APPROVED")
                .adminNote("Duyệt thành công")
                .build();

        when(currentUserProvider.resolveUserId(any())).thenReturn(adminId);
        when(walletService.reviewWithdrawal(eq(adminId), eq(withdrawalId), any(WalletWithdrawalReviewRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/wallets/admin/withdrawals/{id}/review", withdrawalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(withdrawalId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.adminNote").value("Duyệt thành công"));
    }
}

package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO;
import com.secondlife.secondlife.enums.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
import java.util.UUID;

public interface WalletQueryService {
    WalletResponseDTO getWallet(UUID userId);
    Page<WalletTransactionResponseDTO> getTransactions(UUID userId, Pageable pageable);
    Page<WalletTransactionResponseDTO> getTransactions(UUID userId, WalletTransactionType type,
                                                      Instant from, Instant to, Pageable pageable);
    WalletTransactionResponseDTO getTransaction(UUID userId, UUID transactionId);
}

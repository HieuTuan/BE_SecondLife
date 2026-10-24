package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface WalletQueryService {
    WalletResponseDTO getWallet(UUID userId);
    Page<WalletTransactionResponseDTO> getTransactions(UUID userId, Pageable pageable);
}

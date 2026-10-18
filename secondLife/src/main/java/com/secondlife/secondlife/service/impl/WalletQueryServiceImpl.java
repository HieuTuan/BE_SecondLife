package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO;
import com.secondlife.secondlife.mapper.WalletMapper;
import com.secondlife.secondlife.service.WalletQueryService;
import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletQueryServiceImpl implements WalletQueryService {

    private final WalletService walletService;
    private final WalletMapper walletMapper;

    @Override
    @Transactional
    public WalletResponseDTO getWallet(UUID userId) {
        return walletMapper.toWalletResponse(walletService.getWalletByUserId(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponseDTO> getTransactions(UUID userId, Pageable pageable) {
        return walletService.getTransactions(userId, pageable).map(walletMapper::toTransactionResponse);
    }
}

package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.credit.CreditBalanceResponse;
import com.secondlife.secondlife.entity.CreditBalance;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.repository.CreditBalanceRepository;
import com.secondlife.secondlife.service.CreditBalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditBalanceServiceImpl implements CreditBalanceService {
    private final CreditBalanceRepository balanceRepository;

    @Override
    @Transactional(readOnly = true)
    public CreditBalanceResponse getBalance(UUID sellerId) {
        long listing = 0;
        long valuation = 0;
        for (CreditBalance balance : balanceRepository.findByUserId(sellerId)) {
            if (balance.getCreditType() == CreditType.LISTING) {
                listing = balance.getQuantity();
            } else if (balance.getCreditType() == CreditType.VALUATION) {
                valuation = balance.getQuantity();
            }
        }
        return new CreditBalanceResponse(listing, valuation);
    }
}

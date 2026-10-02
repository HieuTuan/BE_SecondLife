package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.credit.CreditBalanceResponse;

import java.util.UUID;

public interface CreditBalanceService {
    CreditBalanceResponse getBalance(UUID sellerId);
}

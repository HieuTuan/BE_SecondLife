package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.CreditBalanceResponse;
import com.secondlife.secondlife.entity.TopupPackage;

import java.util.List;
import java.util.UUID;

public interface CreditService {
    CreditBalanceResponse getUserCredit(UUID userId);
    List<TopupPackage> getAllTopupPackages();
}

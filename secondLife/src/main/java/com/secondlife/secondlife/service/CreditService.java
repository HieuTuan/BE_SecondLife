package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.dto.credit.CreditBalanceResponse;

import java.util.List;
import java.util.UUID;

public interface CreditService {
    CreditBalanceResponse getUserCredit(UUID userId);
    List<TopupPackage> getAllTopupPackages();
    CreditBalanceResponse purchaseTopupPackage(UUID userId, UUID packageId);
}

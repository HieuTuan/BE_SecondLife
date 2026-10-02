package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.entity.UserCredit;

import java.util.List;
import java.util.UUID;

public interface CreditService {
    UserCredit getUserCredit(UUID userId);
    List<TopupPackage> getAllTopupPackages();
    UserCredit purchaseTopupPackage(UUID userId, UUID packageId);
    void deductPostCredit(UUID userId);
    void deductChatCredit(UUID userId);
}

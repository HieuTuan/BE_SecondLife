package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.CreditBalanceResponse;
import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.repository.TopupPackageRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.CreditBalanceService;
import com.secondlife.secondlife.service.CreditService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CreditServiceImpl implements CreditService {
    private final TopupPackageRepository topupPackageRepository;
    private final UserRepository userRepository;
    private final CreditBalanceService creditBalanceService;

    public CreditServiceImpl(TopupPackageRepository topupPackageRepository,
                             UserRepository userRepository, CreditBalanceService creditBalanceService) {
        this.topupPackageRepository = topupPackageRepository;
        this.userRepository = userRepository;
        this.creditBalanceService = creditBalanceService;
    }

    @Override
    @Transactional(readOnly = true)
    public CreditBalanceResponse getUserCredit(UUID userId) {
        // Create user check
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return creditBalanceService.getBalance(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopupPackage> getAllTopupPackages() {
        return topupPackageRepository.findByActiveTrueOrderByNameAscIdAsc();
    }

}

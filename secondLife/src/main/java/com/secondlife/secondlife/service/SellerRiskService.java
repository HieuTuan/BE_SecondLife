package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.EkycResult;
import com.secondlife.secondlife.dto.response.SellerRiskResult;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import java.util.Optional;

public interface SellerRiskService {

    SellerRiskResult evaluateRisk(User user, SellerVerification verification, EkycResult ekycResult);

    Optional<SellerRiskResult> checkIdentityRestriction(SellerVerification verification);
}

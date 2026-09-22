package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.dto.risk.SellerRiskResult;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;

public interface SellerRiskService {

    SellerRiskResult evaluateRisk(User user, SellerVerification verification, EkycResult ekycResult);
}

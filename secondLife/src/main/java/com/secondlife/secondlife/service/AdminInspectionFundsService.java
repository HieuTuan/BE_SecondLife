package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse;

import java.util.UUID;

public interface AdminInspectionFundsService {
    AdminInspectionFundsResponse getFunds(UUID inspectionOrderId);
}

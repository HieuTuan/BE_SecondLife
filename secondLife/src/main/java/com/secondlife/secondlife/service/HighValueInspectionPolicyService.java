package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.HighValueInspectionPolicyRequest;
import com.secondlife.secondlife.dto.response.HighValueInspectionPolicyResponse;
import java.util.UUID;

public interface HighValueInspectionPolicyService {
    HighValueInspectionPolicyResponse get();
    HighValueInspectionPolicyResponse update(UUID adminId, HighValueInspectionPolicyRequest request);
}

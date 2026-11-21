package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.HighValueInspectionPolicyRequest;
import com.secondlife.secondlife.dto.response.HighValueInspectionPolicyResponse;
import com.secondlife.secondlife.entity.HighValueInspectionPolicy;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.HighValueInspectionPolicyRepository;
import com.secondlife.secondlife.service.HighValueInspectionPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HighValueInspectionPolicyServiceImpl implements HighValueInspectionPolicyService {
    private final HighValueInspectionPolicyRepository policies;

    @Override
    @Transactional(readOnly = true)
    public HighValueInspectionPolicyResponse get() {
        return response(policies.findById((short) 1)
                .orElseThrow(() -> new ConflictException("Chưa cấu hình ngưỡng kiểm định hàng giá trị cao")));
    }

    @Override
    @Transactional
    public HighValueInspectionPolicyResponse update(UUID adminId, HighValueInspectionPolicyRequest request) {
        var policy = policies.findForUpdate()
                .orElseThrow(() -> new ConflictException("Chưa cấu hình ngưỡng kiểm định hàng giá trị cao"));
        policy.setThresholdPrice(request.thresholdPrice());
        policy.setUpdatedBy(adminId);
        policy.setUpdatedAt(Instant.now());
        return response(policies.saveAndFlush(policy));
    }

    private HighValueInspectionPolicyResponse response(HighValueInspectionPolicy policy) {
        return new HighValueInspectionPolicyResponse(policy.getThresholdPrice(), "VND", policy.getVersion(),
                policy.getUpdatedAt(), policy.getUpdatedBy());
    }
}

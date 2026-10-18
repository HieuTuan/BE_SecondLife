package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.TopupPackageResponse;
import com.secondlife.secondlife.mapper.TopupPackageMapper;
import com.secondlife.secondlife.service.CreditService;
import com.secondlife.secondlife.service.TopupPackageQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TopUpPackageQueryServiceImpl implements TopupPackageQueryService {

    private final CreditService creditService;
    private final TopupPackageMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<TopupPackageResponse> getPackages() {
        return creditService.getAllTopupPackages().stream().map(mapper::toResponse).toList();
    }
}

package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.TopupPackageRequest;
import com.secondlife.secondlife.dto.response.TopupPackageResponse;
import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.TopupPackageMapper;
import com.secondlife.secondlife.repository.TopupPackageRepository;
import com.secondlife.secondlife.service.AdminTopupPackageService;
import com.secondlife.secondlife.service.CreditPricingService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTopupPackageServiceImpl implements AdminTopupPackageService {
    private final TopupPackageRepository repository;
    private final TopupPackageMapper mapper;
    private final CreditPricingService pricing;
    private final TopupPackagePricingPolicy policy;

    @Override
    @Transactional(readOnly = true)
    public List<TopupPackageResponse> getPackages() {
        var rules = pricing.getAdminPrices();
        return repository.findAll(Sort.by("name", "id")).stream()
                .map(pkg -> mapper.toResponse(pkg, policy.calculate(pkg, rules))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TopupPackageResponse getPackage(UUID packageId) {
        return response(requirePackage(packageId));
    }

    @Override
    @Transactional
    public TopupPackageResponse createPackage(TopupPackageRequest request) {
        TopupPackage entity = mapper.toEntity(request);
        validate(entity);
        return response(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public TopupPackageResponse updatePackage(UUID packageId, TopupPackageRequest request) {
        TopupPackage entity = requirePackageForUpdate(packageId);
        mapper.updateEntity(request, entity);
        validate(entity);
        return response(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional
    public void deletePackage(UUID packageId) {
        setInactive(requirePackageForUpdate(packageId));
    }

    @Override
    @Transactional
    public TopupPackageResponse setActive(UUID packageId, boolean active) {
        var entity = requirePackageForUpdate(packageId);
        if (active) validate(entity);
        entity.setActive(active);
        return response(repository.saveAndFlush(entity));
    }

    private void setInactive(TopupPackage entity) {
        entity.setActive(false);
        repository.saveAndFlush(entity);
    }

    private void validate(TopupPackage entity) {
        policy.validateDefinition(entity);
        policy.requireConfigured(policy.calculate(entity, pricing.getAdminPrices()));
    }

    private TopupPackageResponse response(TopupPackage entity) {
        return mapper.toResponse(entity, policy.calculate(entity, pricing.getAdminPrices()));
    }

    private TopupPackage requirePackageForUpdate(UUID packageId) {
        return repository.findByIdForUpdate(packageId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy gói nạp"));
    }

    private TopupPackage requirePackage(UUID packageId) {
        return repository.findById(packageId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy gói nạp"));
    }
}

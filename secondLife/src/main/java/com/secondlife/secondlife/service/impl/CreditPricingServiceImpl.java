package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.credit.*;
import com.secondlife.secondlife.entity.CreditDiscountTier;
import com.secondlife.secondlife.entity.CreditPricingRule;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.CreditDiscountTierRepository;
import com.secondlife.secondlife.repository.CreditPricingRuleRepository;
import com.secondlife.secondlife.service.CreditPricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditPricingServiceImpl implements CreditPricingService {
    private final CreditPricingRuleRepository priceRepository;
    private final CreditDiscountTierRepository tierRepository;

    @Override
    @Transactional(readOnly = true)
    public CreditPricingResponse getPricing(Integer listingQuantity, Integer valuationQuantity) {
        CreditQuoteResponse quote = null;
        if (listingQuantity != null || valuationQuantity != null) {
            quote = quote(listingQuantity == null ? 0 : listingQuantity,
                    valuationQuantity == null ? 0 : valuationQuantity);
        }
        return new CreditPricingResponse(getAdminPrices(), tierRepository.findByActiveTrueOrderByMinQuantityAsc()
                .stream().map(this::toTierResponse).toList(), quote);
    }

    @Override
    @Transactional(readOnly = true)
    public CreditQuoteResponse quote(int listingQuantity, int valuationQuantity) {
        if (listingQuantity < 0 || valuationQuantity < 0) {
            throw new BadRequestException("Credit quantities cannot be negative");
        }
        int total;
        try {
            total = Math.addExact(listingQuantity, valuationQuantity);
        } catch (ArithmeticException ex) {
            throw new BadRequestException("Credit quantity is too large");
        }
        if (total == 0) {
            throw new BadRequestException("At least one credit is required");
        }

        CreditPricingRule listing = activeRule(CreditType.LISTING);
        CreditPricingRule valuation = activeRule(CreditType.VALUATION);
        if (!listing.getCurrency().equals(valuation.getCurrency())) {
            throw new ConflictException("Credit prices use different currencies");
        }
        CreditDiscountTier tier = tierRepository.findByActiveTrueOrderByMinQuantityAsc().stream()
                .filter(t -> total >= t.getMinQuantity()
                        && (t.getMaxQuantity() == null || total <= t.getMaxQuantity()))
                .findFirst()
                .orElseThrow(() -> new ConflictException("No discount tier covers this credit quantity"));

        BigDecimal subtotal = listing.getUnitPrice().multiply(BigDecimal.valueOf(listingQuantity))
                .add(valuation.getUnitPrice().multiply(BigDecimal.valueOf(valuationQuantity)))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = subtotal.multiply(tier.getDiscountRate()).setScale(2, RoundingMode.HALF_UP);
        return new CreditQuoteResponse(listingQuantity, valuationQuantity,
                listing.getUnitPrice(), valuation.getUnitPrice(), tier.getId(), tier.getMinQuantity(),
                tier.getMaxQuantity(), tier.getDiscountRate(), subtotal, discount,
                subtotal.subtract(discount), listing.getCurrency());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditPricingRuleResponse> getAdminPrices() {
        return priceRepository.findAll().stream()
                .sorted(Comparator.comparing(CreditPricingRule::getCreditType))
                .map(this::toPriceResponse).toList();
    }

    @Override
    @Transactional
    public CreditPricingRuleResponse updatePrice(UUID adminId, CreditType creditType,
                                                  UpdateCreditPricingRequest request) {
        if (request == null || request.unitPrice() == null || request.unitPrice().signum() <= 0
                || request.unitPrice().scale() > 2) {
            throw new BadRequestException("Unit price must be positive with at most two decimal places");
        }
        CreditPricingRule rule = priceRepository.findByCreditType(creditType)
                .orElseThrow(() -> new NotFoundException("Credit price not found"));
        rule.setUnitPrice(request.unitPrice().setScale(2, RoundingMode.UNNECESSARY));
        rule.setActive(true);
        rule.setUpdatedBy(adminId);
        rule.setUpdatedAt(Instant.now());
        return toPriceResponse(priceRepository.save(rule));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CreditDiscountTierResponse> getAdminTiers() {
        return tierRepository.findAllByOrderByMinQuantityAsc().stream().map(this::toTierResponse).toList();
    }

    @Override
    @Transactional
    public CreditDiscountTierResponse createTier(UUID adminId, SaveCreditDiscountTierRequest request) {
        validateTier(request, null);
        CreditDiscountTier tier = new CreditDiscountTier();
        applyTier(tier, adminId, request);
        try {
            return toTierResponse(tierRepository.saveAndFlush(tier));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Credit discount tier conflicts with another tier");
        }
    }

    @Override
    @Transactional
    public CreditDiscountTierResponse updateTier(UUID adminId, UUID tierId,
                                                   SaveCreditDiscountTierRequest request) {
        CreditDiscountTier tier = tierRepository.findById(tierId)
                .orElseThrow(() -> new NotFoundException("Credit discount tier not found"));
        validateTier(request, tierId);
        applyTier(tier, adminId, request);
        try {
            return toTierResponse(tierRepository.saveAndFlush(tier));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Credit discount tier conflicts with another tier");
        }
    }

    private void validateTier(SaveCreditDiscountTierRequest request, UUID currentId) {
        if (request == null || request.minQuantity() == null || request.minQuantity() < 1
                || request.discountRate() == null || request.discountRate().signum() < 0
                || request.discountRate().compareTo(BigDecimal.ONE) >= 0
                || request.discountRate().scale() > 4 || request.active() == null
                || (request.maxQuantity() != null && request.maxQuantity() < request.minQuantity())) {
            throw new BadRequestException("Invalid credit discount tier");
        }
        for (CreditDiscountTier existing : tierRepository.findAll()) {
            if (existing.getId().equals(currentId)) continue;
            if (existing.getMinQuantity() == request.minQuantity()) {
                throw new ConflictException("A credit discount tier already starts at this quantity");
            }
            if (request.active() && existing.isActive()
                    && request.minQuantity() <= upper(existing.getMaxQuantity())
                    && existing.getMinQuantity() <= upper(request.maxQuantity())) {
                throw new ConflictException("Credit discount tiers cannot overlap");
            }
        }
    }

    private long upper(Integer max) {
        return max == null ? Long.MAX_VALUE : max;
    }

    private void applyTier(CreditDiscountTier tier, UUID adminId, SaveCreditDiscountTierRequest request) {
        tier.setMinQuantity(request.minQuantity());
        tier.setMaxQuantity(request.maxQuantity());
        tier.setDiscountRate(request.discountRate().setScale(4, RoundingMode.UNNECESSARY));
        tier.setActive(request.active());
        tier.setUpdatedAt(Instant.now());
        tier.setUpdatedBy(adminId);
    }

    private CreditPricingRule activeRule(CreditType type) {
        CreditPricingRule rule = priceRepository.findByCreditType(type)
                .orElseThrow(() -> new ConflictException("Credit pricing is not configured"));
        if (!rule.isActive() || rule.getUnitPrice() == null) {
            throw new ConflictException("Credit pricing is not configured for " + type);
        }
        return rule;
    }

    private CreditPricingRuleResponse toPriceResponse(CreditPricingRule rule) {
        return new CreditPricingRuleResponse(rule.getCreditType(), rule.getUnitPrice(),
                rule.getCurrency(), rule.isActive());
    }

    private CreditDiscountTierResponse toTierResponse(CreditDiscountTier tier) {
        return new CreditDiscountTierResponse(tier.getId(), tier.getMinQuantity(),
                tier.getMaxQuantity(), tier.getDiscountRate(), tier.isActive());
    }
}

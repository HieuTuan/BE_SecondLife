package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.credit.*;
import com.secondlife.secondlife.entity.CreditPricingRule;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.CreditPricingRuleRepository;
import com.secondlife.secondlife.service.CreditPricingService;
import lombok.RequiredArgsConstructor;
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

    @Override
    @Transactional(readOnly = true)
    public CreditPricingResponse getPricing(Integer listingQuantity, Integer valuationQuantity) {
        CreditQuoteResponse quote = null;
        if (listingQuantity != null || valuationQuantity != null) {
            quote = quote(listingQuantity == null ? 0 : listingQuantity,
                    valuationQuantity == null ? 0 : valuationQuantity);
        }
        return new CreditPricingResponse(getAdminPrices(), quote);
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
        BigDecimal subtotal = listing.getUnitPrice().multiply(BigDecimal.valueOf(listingQuantity))
                .add(valuation.getUnitPrice().multiply(BigDecimal.valueOf(valuationQuantity)))
                .setScale(2, RoundingMode.HALF_UP);
        return new CreditQuoteResponse(listingQuantity, valuationQuantity,
                listing.getUnitPrice(), valuation.getUnitPrice(), subtotal, subtotal, listing.getCurrency());
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
}

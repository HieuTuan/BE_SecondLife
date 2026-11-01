package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.CreditPricingRuleResponse;
import com.secondlife.secondlife.dto.response.TopupPackagePriceResponse;
import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class TopupPackagePricingPolicy {
    public TopupPackagePriceResponse calculate(TopupPackage pkg, List<CreditPricingRuleResponse> rules) {
        BigDecimal listing = unitPrice(rules, CreditType.LISTING, pkg.getPostCredits());
        BigDecimal chat = unitPrice(rules, CreditType.AI_CHAT, pkg.getChatCredits());
        BigDecimal valuation = unitPrice(rules, CreditType.VALUATION, pkg.getValuationCredits());
        if (listing == null || chat == null || valuation == null) {
            return new TopupPackagePriceResponse(false, null, null, listing, chat, valuation);
        }
        BigDecimal reference = listing.multiply(BigDecimal.valueOf(pkg.getPostCredits()))
                .add(chat.multiply(BigDecimal.valueOf(pkg.getChatCredits())))
                .add(valuation.multiply(BigDecimal.valueOf(pkg.getValuationCredits())))
                .setScale(2, RoundingMode.HALF_UP);
        boolean valid = pkg.getPostCredits() >= 0 && pkg.getChatCredits() >= 0 && pkg.getValuationCredits() >= 0
                && reference.signum() > 0 && pkg.getPrice() != null
                && pkg.getPrice().compareTo(new BigDecimal("1000")) >= 0
                && pkg.getPrice().stripTrailingZeros().scale() <= 2
                && pkg.getPrice().compareTo(reference) <= 0;
        BigDecimal discount = valid ? reference.subtract(pkg.getPrice()).multiply(BigDecimal.valueOf(100))
                .divide(reference, 2, RoundingMode.HALF_UP) : null;
        return new TopupPackagePriceResponse(valid, reference, discount, listing, chat, valuation);
    }

    public void requireConfigured(TopupPackagePriceResponse pricing) {
        if (!pricing.pricingAvailable()) {
            throw new ConflictException("Gói chưa có giá hợp lệ. Cần cấu hình đơn giá VND đang hoạt động và giá gói không vượt giá mua riêng.");
        }
    }

    public void validateDefinition(TopupPackage pkg) {
        if (pkg.getPostCredits() < 0 || pkg.getChatCredits() < 0 || pkg.getValuationCredits() < 0
                || ((long) pkg.getPostCredits() + pkg.getChatCredits() + pkg.getValuationCredits()) == 0) {
            throw new BadRequestException("Gói phải có ít nhất một lượt sử dụng và số lượt không được âm");
        }
    }

    private BigDecimal unitPrice(List<CreditPricingRuleResponse> rules, CreditType type, int quantity) {
        if (quantity == 0) return BigDecimal.ZERO.setScale(2);
        return rules.stream().filter(rule -> rule.creditType() == type && rule.active()
                        && "VND".equals(rule.currency()) && rule.unitPrice() != null && rule.unitPrice().signum() > 0)
                .map(CreditPricingRuleResponse::unitPrice).findFirst().orElse(null);
    }
}

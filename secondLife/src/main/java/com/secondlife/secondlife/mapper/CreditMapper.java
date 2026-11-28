package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.CreditLedgerResponse;
import com.secondlife.secondlife.dto.response.CreditPricingRuleResponse;
import com.secondlife.secondlife.dto.response.CreditPurchaseResponse;
import com.secondlife.secondlife.dto.response.CreditQuoteResponse;
import com.secondlife.secondlife.entity.CreditLedgerEntry;
import com.secondlife.secondlife.entity.CreditPricingRule;
import com.secondlife.secondlife.entity.CreditPurchase;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class CreditMapper {
    private final Delegate delegate;

    public CreditMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public CreditPurchaseResponse toPurchaseResponse(CreditPurchase purchase) {
        return delegate.toPurchaseResponse(purchase);
    }

    protected CreditQuoteResponse toSnapshot(CreditPurchase purchase) {
        return delegate.toSnapshot(purchase);
    }

    public CreditLedgerResponse toLedgerResponse(CreditLedgerEntry entry) {
        return delegate.toLedgerResponse(entry);
    }

    public CreditPricingRuleResponse toPriceResponse(CreditPricingRule rule) {
        return delegate.toPriceResponse(rule);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "id", source = "purchase.id")
        @Mapping(target = "status", source = "purchase.status")
        @Mapping(target = "snapshot", source = "purchase")
        @Mapping(target = "createdAt", source = "purchase.createdAt")
        @Mapping(target = "paidAt", source = "purchase.paidAt")
        CreditPurchaseResponse toPurchaseResponse(CreditPurchase purchase);

        CreditQuoteResponse toSnapshot(CreditPurchase purchase);

        CreditLedgerResponse toLedgerResponse(CreditLedgerEntry entry);

        CreditPricingRuleResponse toPriceResponse(CreditPricingRule rule);
    }
}

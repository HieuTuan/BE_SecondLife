package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.credit.*;
import com.secondlife.secondlife.entity.CreditLedgerEntry;
import com.secondlife.secondlife.entity.CreditPurchase;
import com.secondlife.secondlife.entity.PaymentIntent;
import com.secondlife.secondlife.enums.CreditPurchaseStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import com.secondlife.secondlife.repository.CreditPurchaseRepository;
import com.secondlife.secondlife.repository.PaymentIntentRepository;
import com.secondlife.secondlife.service.CreditPricingService;
import com.secondlife.secondlife.service.CreditPurchaseService;
import com.secondlife.secondlife.service.payment.PaymentIntentRequest;
import com.secondlife.secondlife.service.payment.PaymentIntentResult;
import com.secondlife.secondlife.service.payment.PaymentProvider;
import com.secondlife.secondlife.service.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditPurchaseServiceImpl implements CreditPurchaseService {
    private final CreditPricingService pricingService;
    private final CreditPurchaseRepository purchaseRepository;
    private final PaymentIntentRepository intentRepository;
    private final CreditLedgerRepository ledgerRepository;
    private final PaymentProvider paymentProvider;

    @Override
    @Transactional
    public CreditPurchaseResponse createPurchase(UUID sellerId, CreateCreditPurchaseRequest request) {
        if (sellerId == null || request == null || request.listingQuantity() == null
                || request.valuationQuantity() == null) {
            throw new BadRequestException("Credit purchase quantities are required");
        }
        CreditQuoteResponse quote = pricingService.quote(request.listingQuantity(), request.valuationQuantity());
        if (quote.finalFee().compareTo(BigDecimal.ZERO) <= 0
                || !fitsMoneyColumn(quote.subtotal())
                || !fitsMoneyColumn(quote.finalFee())) {
            throw new BadRequestException("Credit purchase amount is invalid");
        }

        CreditPurchase purchase = new CreditPurchase();
        purchase.setUserId(sellerId);
        purchase.setListingQuantity(quote.listingQuantity());
        purchase.setValuationQuantity(quote.valuationQuantity());
        purchase.setListingUnitPrice(quote.listingUnitPrice());
        purchase.setValuationUnitPrice(quote.valuationUnitPrice());
        purchase.setSubtotal(quote.subtotal());
        purchase.setFinalFee(quote.finalFee());
        purchase.setCurrency(quote.currency());
        purchase.setStatus(CreditPurchaseStatus.PAYMENT_PENDING);
        purchase.setCreatedAt(Instant.now());
        purchaseRepository.saveAndFlush(purchase);

        PaymentIntentResult result = paymentProvider.createIntent(
                new PaymentIntentRequest(purchase.getId(), quote.finalFee(), quote.currency()));
        if (result == null || !paymentProvider.providerName().equals(result.provider())
                || result.provider() == null || result.provider().length() > 30
                || result.providerIntentId() == null || result.providerIntentId().isBlank()
                || result.providerIntentId().length() > 150 || result.status() != PaymentStatus.PENDING
                || result.amount() == null || result.amount().compareTo(quote.finalFee()) != 0
                || !quote.currency().equals(result.currency())) {
            throw new ConflictException("Payment provider returned an invalid intent");
        }
        PaymentIntent intent = new PaymentIntent();
        intent.setPurchaseId(purchase.getId());
        intent.setProvider(result.provider());
        intent.setProviderIntentId(result.providerIntentId());
        intent.setAmount(result.amount());
        intent.setCurrency(result.currency());
        intent.setStatus(PaymentStatus.PENDING);
        intent.setCreatedAt(Instant.now());
        intentRepository.save(intent);
        return toResponse(purchase, intent);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CreditPurchaseResponse> getPurchases(UUID sellerId, Pageable pageable) {
        return PageResponse.from(purchaseRepository.findByUserIdOrderByCreatedAtDesc(sellerId, pageable)
                .map(purchase -> toResponse(purchase,
                        intentRepository.findByPurchaseId(purchase.getId()).orElse(null))));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CreditLedgerResponse> getLedger(UUID sellerId, Pageable pageable) {
        return PageResponse.from(ledgerRepository.findByUserIdOrderByCreatedAtDesc(sellerId, pageable)
                .map(this::toLedgerResponse));
    }

    private CreditPurchaseResponse toResponse(CreditPurchase purchase, PaymentIntent intent) {
        CreditQuoteResponse snapshot = new CreditQuoteResponse(purchase.getListingQuantity(),
                purchase.getValuationQuantity(), purchase.getListingUnitPrice(),
                purchase.getValuationUnitPrice(), purchase.getSubtotal(),
                purchase.getFinalFee(), purchase.getCurrency());
        return new CreditPurchaseResponse(purchase.getId(), purchase.getStatus(), snapshot,
                intent == null ? null : intent.getId(), intent == null ? null : intent.getProvider(),
                intent == null ? null : intent.getProviderIntentId(),
                intent == null ? null : intent.getStatus(), purchase.getCreatedAt(), purchase.getPaidAt());
    }

    private CreditLedgerResponse toLedgerResponse(CreditLedgerEntry entry) {
        return new CreditLedgerResponse(entry.getId(), entry.getCreditType(), entry.getPurchaseId(),
                entry.getEntryType(), entry.getQuantityDelta(), entry.getBalanceAfter(), entry.getCreatedAt());
    }

    private boolean fitsMoneyColumn(BigDecimal value) {
        return value != null && value.scale() <= 2 && value.precision() - value.scale() <= 17;
    }
}

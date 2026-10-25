package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateCreditPurchaseRequest;
import com.secondlife.secondlife.dto.response.CreditLedgerResponse;
import com.secondlife.secondlife.dto.response.CreditPurchaseResponse;
import com.secondlife.secondlife.dto.response.CreditQuoteResponse;
import com.secondlife.secondlife.entity.CreditLedgerEntry;
import com.secondlife.secondlife.entity.CreditPurchase;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.entity.PaymentIntent;
import com.secondlife.secondlife.enums.CreditPurchaseStatus;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.integration.payment.PaymentProvider;
import com.secondlife.secondlife.mapper.CreditMapper;
import com.secondlife.secondlife.repository.CreditBalanceRepository;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import com.secondlife.secondlife.repository.CreditPurchaseRepository;
import com.secondlife.secondlife.repository.PaymentIntentRepository;
import com.secondlife.secondlife.service.CreditPricingService;
import com.secondlife.secondlife.service.CreditPurchaseService;
import com.secondlife.secondlife.service.WalletService;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditPurchaseServiceImpl implements CreditPurchaseService {

    private final CreditPricingService pricingService;
    private final CreditPurchaseRepository purchaseRepository;
    private final PaymentIntentRepository intentRepository;
    private final CreditLedgerRepository ledgerRepository;
    private final PaymentProvider paymentProvider;
    private final WalletService walletService;
    private final CreditBalanceRepository balanceRepository;
    private final CreditMapper creditMapper;

    @Override
    @Transactional
    public CreditPurchaseResponse createPurchase(UUID sellerId, CreateCreditPurchaseRequest request) {
        if (sellerId == null || request == null || request.listingQuantity() == null
                || request.valuationQuantity() == null || request.aiChatQuantity() == null) {
            throw new BadRequestException("Credit purchase quantities are required");
        }
        CreditQuoteResponse quote = pricingService.quote(request.listingQuantity(), request.valuationQuantity(), request.aiChatQuantity());
        if (quote.finalFee().compareTo(BigDecimal.ZERO) <= 0
                || !fitsMoneyColumn(quote.subtotal())
                || !fitsMoneyColumn(quote.finalFee())) {
            throw new BadRequestException("Credit purchase amount is invalid");
        }

        CreditPurchase purchase = new CreditPurchase();
        purchase.setUserId(sellerId);
        purchase.setListingQuantity(quote.listingQuantity());
        purchase.setValuationQuantity(quote.valuationQuantity());
        purchase.setAiChatQuantity(quote.aiChatQuantity());
        purchase.setListingUnitPrice(quote.listingUnitPrice());
        purchase.setValuationUnitPrice(quote.valuationUnitPrice());
        purchase.setAiChatUnitPrice(quote.aiChatUnitPrice());
        purchase.setSubtotal(quote.subtotal());
        purchase.setFinalFee(quote.finalFee());
        purchase.setCurrency(quote.currency());
        purchase.setStatus(CreditPurchaseStatus.PAID);
        purchase.setDiscountMinQuantity(0);
        purchase.setDiscountRate(BigDecimal.ZERO);
        purchase.setDiscountAmount(BigDecimal.ZERO);
        purchase.setCreatedAt(Instant.now());
        purchase.setPaidAt(Instant.now());
        purchaseRepository.saveAndFlush(purchase);

        // Deduct from wallet directly
        walletService.processPayment(sellerId, quote.finalFee(), purchase.getId(),
                WalletTransactionReferenceType.CREDIT_PURCHASE);

        // Grant credits directly since payment is completed
        grant(purchase, CreditType.LISTING, purchase.getListingQuantity());
        grant(purchase, CreditType.VALUATION, purchase.getValuationQuantity());
        grant(purchase, CreditType.AI_CHAT, purchase.getAiChatQuantity());

        return creditMapper.toPurchaseResponse(purchase, null);
    }

    private void grant(CreditPurchase purchase, CreditType type, int quantity) {
        if (quantity <= 0) return;
        long balanceAfter = balanceRepository.grant(purchase.getUserId(), type, quantity);

        CreditLedgerEntry entry = new CreditLedgerEntry();
        entry.setUserId(purchase.getUserId());
        entry.setCreditType(type);
        entry.setPurchaseId(purchase.getId());
        entry.setEntryType("PURCHASE");
        entry.setQuantityDelta(quantity);
        entry.setBalanceAfter(balanceAfter);
        entry.setIdempotencyKey("PURCHASE:" + purchase.getId() + ":" + type.name());
        entry.setCreatedAt(Instant.now());
        ledgerRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CreditPurchaseResponse> getPurchases(UUID sellerId, Pageable pageable) {
        var page = purchaseRepository.findByUserIdOrderByCreatedAtDesc(sellerId, pageable);
        Map<UUID, PaymentIntent> intents = new HashMap<>();
        if (!page.isEmpty()) {
            for (PaymentIntent intent : intentRepository.findByPurchaseIdIn(page.stream().map(CreditPurchase::getId).toList())) {
                if (intents.putIfAbsent(intent.getPurchaseId(), intent) != null) {
                    throw new IncorrectResultSizeDataAccessException(1);
                }
            }
        }
        return PageResponse.from(page.map(purchase -> creditMapper.toPurchaseResponse(purchase, intents.get(purchase.getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CreditLedgerResponse> getLedger(UUID sellerId, Pageable pageable) {
        return PageResponse.from(ledgerRepository.findByUserIdOrderByCreatedAtDesc(sellerId, pageable)
                .map(creditMapper::toLedgerResponse));
    }

    private boolean fitsMoneyColumn(BigDecimal value) {
        return value != null && value.scale() <= 2 && value.precision() - value.scale() <= 17;
    }
}

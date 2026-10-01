package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.CreditLedgerEntry;
import com.secondlife.secondlife.entity.CreditPurchase;
import com.secondlife.secondlife.entity.PaymentIntent;
import com.secondlife.secondlife.enums.CreditPurchaseStatus;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.CreditBalanceGrantRepository;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import com.secondlife.secondlife.repository.CreditPurchaseRepository;
import com.secondlife.secondlife.repository.PaymentIntentRepository;
import com.secondlife.secondlife.service.CreditPaymentCallbackService;
import com.secondlife.secondlife.service.payment.NormalizedPaymentCallback;
import com.secondlife.secondlife.service.payment.PaymentCallbackInboxService;
import com.secondlife.secondlife.service.payment.PaymentProvider;
import com.secondlife.secondlife.service.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CreditPaymentCallbackServiceImpl implements CreditPaymentCallbackService {
    private final PaymentProvider paymentProvider;
    private final PaymentIntentRepository intentRepository;
    private final CreditPurchaseRepository purchaseRepository;
    private final PaymentCallbackInboxService callbackInbox;
    private final CreditBalanceGrantRepository balanceGrantRepository;
    private final CreditLedgerRepository ledgerRepository;

    @Override
    @Transactional
    public void processMockCallback(String rawBody, String signature) {
        NormalizedPaymentCallback callback = paymentProvider.verifyCallback(rawBody, signature);
        PaymentIntent intent = intentRepository.findByProviderAndProviderIntentId(
                callback.provider(), callback.providerIntentId())
                .orElseThrow(() -> new NotFoundException("Payment intent not found"));
        CreditPurchase purchase = purchaseRepository.findByIdForUpdate(intent.getPurchaseId())
                .orElseThrow(() -> new NotFoundException("Credit purchase not found"));
        if (callback.amount() == null || callback.amount().compareTo(intent.getAmount()) != 0
                || callback.amount().compareTo(purchase.getFinalFee()) != 0
                || !intent.getCurrency().equals(callback.currency())
                || !purchase.getCurrency().equals(callback.currency())) {
            throw new BadRequestException("Payment callback amount or currency does not match purchase");
        }
        callbackInbox.recordVerifiedCallback(intent.getId(), callback);
        if (purchase.getStatus() == CreditPurchaseStatus.PAID) {
            return;
        }
        if (callback.status() == PaymentStatus.PENDING) {
            return;
        }
        if (callback.status() == PaymentStatus.FAILED) {
            purchase.setStatus(CreditPurchaseStatus.PAYMENT_FAILED);
            intent.setStatus(PaymentStatus.FAILED);
            return;
        }

        grant(purchase, CreditType.LISTING, purchase.getListingQuantity());
        grant(purchase, CreditType.VALUATION, purchase.getValuationQuantity());
        purchase.setStatus(CreditPurchaseStatus.PAID);
        purchase.setPaidAt(Instant.now());
        intent.setStatus(PaymentStatus.SUCCEEDED);
    }

    private void grant(CreditPurchase purchase, CreditType type, int quantity) {
        if (quantity == 0) {
            return;
        }
        long balanceAfter = balanceGrantRepository.grant(purchase.getUserId(), type, quantity);
        CreditLedgerEntry entry = new CreditLedgerEntry();
        entry.setUserId(purchase.getUserId());
        entry.setCreditType(type);
        entry.setPurchaseId(purchase.getId());
        entry.setEntryType("GRANT");
        entry.setQuantityDelta(quantity);
        entry.setBalanceAfter(balanceAfter);
        entry.setIdempotencyKey("CREDIT_GRANT:" + purchase.getId() + ":" + type.name());
        entry.setCreatedAt(Instant.now());
        ledgerRepository.save(entry);
    }
}

package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.PaymentIntent;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.entity.PaymentCallbackEvent;
import com.secondlife.secondlife.repository.PaymentCallbackEventRepository;
import com.secondlife.secondlife.repository.PaymentCallbackInboxRepository;
import com.secondlife.secondlife.repository.PaymentIntentRepository;
import com.secondlife.secondlife.service.payment.NormalizedPaymentCallback;
import com.secondlife.secondlife.service.payment.PaymentCallbackInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCallbackInboxServiceImpl implements PaymentCallbackInboxService {
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentCallbackInboxRepository inboxRepository;
    private final PaymentCallbackEventRepository eventRepository;

    @Override
    @Transactional
    public boolean recordVerifiedCallback(UUID paymentIntentId, NormalizedPaymentCallback callback) {
        if (paymentIntentId == null || callback == null || callback.provider() == null || callback.eventId() == null
                || callback.providerIntentId() == null || callback.status() == null
                || callback.eventId().isBlank()) {
            throw new BadRequestException("Invalid normalized payment callback");
        }
        PaymentIntent intent = paymentIntentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new NotFoundException("Payment intent not found"));
        if (!intent.getProvider().equals(callback.provider())
                || !intent.getProviderIntentId().equals(callback.providerIntentId())) {
            throw new BadRequestException("Payment callback does not match intent");
        }
        // This insert joins the caller's transaction so an unsuccessful grant also rolls it back.
        if (inboxRepository.insertIfAbsent(paymentIntentId, callback)) {
            return true;
        }
        PaymentCallbackEvent existing = eventRepository.findByProviderAndProviderEventId(
                callback.provider(), callback.eventId()).orElseThrow();
        if (!existing.getPaymentIntentId().equals(paymentIntentId)
                || !existing.getNormalizedStatus().equals(callback.status().name())) {
            throw new ConflictException("Payment event ID was reused with different details");
        }
        return false;
    }
}

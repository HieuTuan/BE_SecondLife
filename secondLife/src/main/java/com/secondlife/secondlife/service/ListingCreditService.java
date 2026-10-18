package com.secondlife.secondlife.service;

import com.secondlife.secondlife.enums.CreditType;
import java.util.UUID;

public interface ListingCreditService {
    void consume(UUID userId, CreditType type, String key);

    void consumeImmediately(UUID userId, CreditType type, String key);

    void refund(UUID userId, CreditType type, String key);

}

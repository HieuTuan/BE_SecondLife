package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TopupPackagePurchaseResponse(
        UUID id, UUID requestId, UUID packageId, long packageVersion,
        String packageName, String packageDescription,
        int postCredits, int chatCredits, int valuationCredits,
        BigDecimal price, String currency, BigDecimal referencePrice, BigDecimal discountPercentage,
        BigDecimal listingUnitPrice, BigDecimal chatUnitPrice, BigDecimal valuationUnitPrice,
        BigDecimal walletBalanceBefore, BigDecimal walletBalanceAfter,
        long postBalanceAfter, long chatBalanceAfter, long valuationBalanceAfter, Instant createdAt
) {
}

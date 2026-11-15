package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.SystemWalletDeltaAction;
import com.secondlife.secondlife.enums.SystemWalletFundCategory;
import com.secondlife.secondlife.enums.SystemWalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SystemWalletTransactionResponse(Long id, UUID orderId, SystemWalletTransactionType type,
        BigDecimal heldDelta, BigDecimal shippingDelta, BigDecimal revenueDelta, BigDecimal heldBalanceAfter,
        BigDecimal shippingBalanceAfter, BigDecimal revenueBalanceAfter, Instant createdAt,
        UUID inspectionOrderId, BigDecimal carrierFee, BigDecimal unfundedExpense,
        String entryId, SystemWalletFundCategory fundCategory, BigDecimal totalDelta, SystemWalletDeltaAction deltaAction,
        BigDecimal userShippingFee, String userShippingNote, String breakdownDescription) {

}

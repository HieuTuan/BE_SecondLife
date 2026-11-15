package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.SystemWalletDeltaAction;
import com.secondlife.secondlife.enums.SystemWalletFundCategory;
import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Subselect("SELECT * FROM system_wallet_statement")
@Immutable
@Getter
@NoArgsConstructor
public class SystemWalletStatementEntry {

    @Id
    private String entryId;

    private Long id;
    private int lineNumber;
    private UUID orderId;
    private UUID inspectionOrderId;

    @Enumerated(EnumType.STRING)
    private SystemWalletTransactionType type;

    @Enumerated(EnumType.STRING)
    private SystemWalletFundCategory fundCategory;

    @Enumerated(EnumType.STRING)
    private SystemWalletDeltaAction deltaAction;

    private BigDecimal totalDelta;
    private BigDecimal heldDelta;
    private BigDecimal shippingDelta;
    private BigDecimal revenueDelta;
    private BigDecimal heldBalanceAfter;
    private BigDecimal shippingBalanceAfter;
    private BigDecimal revenueBalanceAfter;
    private BigDecimal carrierFee;
    private BigDecimal unfundedExpense;
    private String userShippingNote;
    private String breakdownDescription;
    private Instant createdAt;
}

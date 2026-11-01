package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "topup_package_purchases")
@Getter
@Setter
@NoArgsConstructor
public class TopupPackagePurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "package_id", nullable = false) private UUID packageId;
    @Column(name = "package_version", nullable = false) private long packageVersion;
    @Column(name = "package_name", nullable = false) private String packageName;
    @Column(name = "package_description") private String packageDescription;
    @Column(name = "post_credits", nullable = false) private int postCredits;
    @Column(name = "chat_credits", nullable = false) private int chatCredits;
    @Column(name = "valuation_credits", nullable = false) private int valuationCredits;
    @Column(nullable = false, precision = 38, scale = 2) private BigDecimal price;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "reference_price", nullable = false, precision = 38, scale = 2) private BigDecimal referencePrice;
    @Column(name = "discount_percentage", nullable = false, precision = 5, scale = 2) private BigDecimal discountPercentage;
    @Column(name = "listing_unit_price", nullable = false, precision = 19, scale = 2) private BigDecimal listingUnitPrice;
    @Column(name = "chat_unit_price", nullable = false, precision = 19, scale = 2) private BigDecimal chatUnitPrice;
    @Column(name = "valuation_unit_price", nullable = false, precision = 19, scale = 2) private BigDecimal valuationUnitPrice;
    @Column(name = "wallet_balance_before", nullable = false, precision = 18, scale = 2) private BigDecimal walletBalanceBefore;
    @Column(name = "wallet_balance_after", nullable = false, precision = 18, scale = 2) private BigDecimal walletBalanceAfter;
    @Column(name = "post_balance_after", nullable = false) private long postBalanceAfter;
    @Column(name = "chat_balance_after", nullable = false) private long chatBalanceAfter;
    @Column(name = "valuation_balance_after", nullable = false) private long valuationBalanceAfter;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}

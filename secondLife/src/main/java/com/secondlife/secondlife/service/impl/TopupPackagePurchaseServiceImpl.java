package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.TopupPackagePurchaseRequest;
import com.secondlife.secondlife.dto.response.TopupPackagePurchaseResponse;
import com.secondlife.secondlife.entity.CreditLedgerEntry;
import com.secondlife.secondlife.entity.TopupPackagePurchase;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.TopupPackageMapper;
import com.secondlife.secondlife.repository.CreditBalanceRepository;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import com.secondlife.secondlife.repository.TopupPackagePurchaseRepository;
import com.secondlife.secondlife.repository.TopupPackageRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.service.CreditBalanceService;
import com.secondlife.secondlife.service.CreditPricingService;
import com.secondlife.secondlife.service.TopupPackagePurchaseService;
import com.secondlife.secondlife.service.WalletService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopupPackagePurchaseServiceImpl implements TopupPackagePurchaseService {
    private final TopupPackageRepository packages;
    private final TopupPackagePurchaseRepository purchases;
    private final UserWalletRepository wallets;
    private final WalletService walletService;
    private final CreditBalanceRepository balances;
    private final CreditLedgerRepository ledger;
    private final CreditBalanceService creditBalances;
    private final CreditPricingService pricing;
    private final TopupPackagePricingPolicy policy;
    private final TopupPackageMapper mapper;

    @Override
    @Transactional
    public TopupPackagePurchaseResponse purchase(UUID userId, UUID packageId, TopupPackagePurchaseRequest request) {
        if (request == null || request.requestId() == null || request.packageVersion() == null
                || request.expectedPrice() == null) {
            throw new BadRequestException("Cần requestId, packageVersion và expectedPrice để mua gói");
        }
        var previous = purchases.findByUserIdAndRequestId(userId, request.requestId());
        if (previous.isPresent()) return replay(previous.get(), packageId, request);
        // Serialize requests for one wallet; admin locks only the package.
        wallets.ensureWalletExists(UUID.randomUUID(), userId);
        var wallet = wallets.findByUserIdWithLock(userId).orElseThrow();
        previous = purchases.findByUserIdAndRequestId(userId, request.requestId());
        if (previous.isPresent()) return replay(previous.get(), packageId, request);
        var pkg = packages.findByIdForPurchase(packageId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy gói dịch vụ"));
        if (!pkg.isActive()) throw new ConflictException("Gói đã ngừng bán. Vui lòng tải lại danh sách.");
        if (pkg.getVersion() != request.packageVersion()
                || pkg.getPrice().compareTo(request.expectedPrice()) != 0) {
            throw new ConflictException("Gói đã thay đổi. Vui lòng tải lại và xác nhận giá/quyền lợi mới.");
        }
        policy.validateDefinition(pkg);
        var calculated = policy.calculate(pkg, pricing.getAdminPrices());
        policy.requireConfigured(calculated);
        var receipt = new TopupPackagePurchase();
        receipt.setUserId(userId);
        receipt.setRequestId(request.requestId());
        receipt.setPackageId(packageId);
        receipt.setPackageVersion(pkg.getVersion());
        receipt.setPackageName(pkg.getName());
        receipt.setPackageDescription(pkg.getDescription());
        receipt.setPostCredits(pkg.getPostCredits());
        receipt.setChatCredits(pkg.getChatCredits());
        receipt.setValuationCredits(pkg.getValuationCredits());
        receipt.setPrice(pkg.getPrice());
        receipt.setCurrency("VND");
        receipt.setReferencePrice(calculated.referencePrice());
        receipt.setDiscountPercentage(calculated.discountPercentage());
        receipt.setListingUnitPrice(calculated.listingUnitPrice());
        receipt.setChatUnitPrice(calculated.chatUnitPrice());
        receipt.setValuationUnitPrice(calculated.valuationUnitPrice());
        receipt.setWalletBalanceBefore(wallet.getBalance());
        walletService.processPayment(userId, pkg.getPrice(), packageId, WalletTransactionReferenceType.TOPUP_PACKAGE);
        grant(userId, CreditType.LISTING, pkg.getPostCredits(), request.requestId());
        grant(userId, CreditType.AI_CHAT, pkg.getChatCredits(), request.requestId());
        grant(userId, CreditType.VALUATION, pkg.getValuationCredits(), request.requestId());
        var after = creditBalances.getBalance(userId);
        receipt.setPostBalanceAfter(after.postCredits());
        receipt.setChatBalanceAfter(after.chatCredits());
        receipt.setValuationBalanceAfter(after.valuationCredits());
        receipt.setWalletBalanceAfter(wallet.getBalance());
        receipt.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return mapper.toPurchaseResponse(purchases.saveAndFlush(receipt));
    }

    private TopupPackagePurchaseResponse replay(TopupPackagePurchase receipt, UUID packageId,
                                                TopupPackagePurchaseRequest request) {
        if (!receipt.getPackageId().equals(packageId) || receipt.getPackageVersion() != request.packageVersion()
                || receipt.getPrice().compareTo(request.expectedPrice()) != 0) {
            throw new ConflictException("requestId đã dùng cho nội dung mua khác");
        }
        return mapper.toPurchaseResponse(receipt);
    }

    private void grant(UUID userId, CreditType type, int quantity, UUID requestId) {
        if (quantity == 0) return;
        long after = balances.grant(userId, type, quantity);
        var entry = new CreditLedgerEntry();
        entry.setUserId(userId);
        entry.setCreditType(type);
        entry.setEntryType("GRANT");
        entry.setQuantityDelta(quantity);
        entry.setBalanceAfter(after);
        entry.setIdempotencyKey("TOPUP_GRANT:" + userId + ":" + requestId + ":" + type.name());
        entry.setCreatedAt(Instant.now());
        ledger.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TopupPackagePurchaseResponse> getMyPurchases(UUID userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("page >= 0, size từ 1 đến 100");
        return PageResponse.from(purchases.findByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .map(mapper::toPurchaseResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public TopupPackagePurchaseResponse getMyPurchase(UUID userId, UUID purchaseId) {
        return mapper.toPurchaseResponse(purchases.findByIdAndUserId(purchaseId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch mua gói")));
    }
}

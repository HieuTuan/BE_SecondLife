package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import com.secondlife.secondlife.entity.WalletWithdrawalStatusHistory;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.WalletTransactionType;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.WalletMapper;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.repository.WalletWithdrawalRepository;
import com.secondlife.secondlife.repository.WalletWithdrawalStatusHistoryRepository;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.WalletService;
import com.secondlife.secondlife.integration.storage.ReportEvidenceStorage;
import com.secondlife.secondlife.dto.response.ReportEvidenceContent;
import com.secondlife.secondlife.dto.response.WalletWithdrawalStatusHistoryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service
public class WalletServiceImpl implements WalletService {
    private final WalletMapper walletMapper;
    private org.springframework.context.ApplicationEventPublisher realtimeEvents;

    @Autowired
    void setRealtimeEvents(org.springframework.context.ApplicationEventPublisher publisher) {
        realtimeEvents = publisher;
    }

    private void saveChangedWallet(UserWallet wallet) {
        userWalletRepository.save(wallet);
        if (realtimeEvents != null) {
            realtimeEvents.publishEvent(new WalletRealtimeDelivery.Changed(wallet.getUser().getId(), wallet.getId()));
        }
    }

    private final UserWalletRepository userWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final WalletWithdrawalRepository walletWithdrawalRepository;
    private final WalletWithdrawalStatusHistoryRepository withdrawalHistoryRepository;
    private final ReportEvidenceStorage reportEvidenceStorage;
    @Value("${app.wallet.withdrawal.bank-warning.version:withdrawal-bank-warning-v1}")
    private String bankWarningVersion = "withdrawal-bank-warning-v1";
    @Value("${app.wallet.withdrawal.max-pending:3}")
    private int maxPendingWithdrawals = 3;
    @Value("${app.wallet.withdrawal.evidence.max-file-size-bytes:${app.media.max-file-size-bytes:10485760}}")
    private long maxEvidenceFileSizeBytes = 10L * 1024 * 1024;

    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository, WalletMapper walletMapper) {
        this(userWalletRepository, walletTransactionRepository, userRepository, null, null, null, null, walletMapper);
    }

    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository,
                             NotificationService notificationService, WalletMapper walletMapper) {
        this(userWalletRepository, walletTransactionRepository, userRepository, notificationService, null, null, null, walletMapper);
    }

    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository,
                             NotificationService notificationService,
                             WalletWithdrawalRepository walletWithdrawalRepository,
                             WalletMapper walletMapper) {
        this(userWalletRepository, walletTransactionRepository, userRepository, notificationService,
                walletWithdrawalRepository, null, null, walletMapper);
    }

    @Autowired
    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository,
                             @Autowired(required = false) NotificationService notificationService,
                             @Autowired(required = false) WalletWithdrawalRepository walletWithdrawalRepository,
                             @Autowired(required = false) WalletWithdrawalStatusHistoryRepository withdrawalHistoryRepository,
                             @Autowired(required = false) ReportEvidenceStorage reportEvidenceStorage,
                             WalletMapper walletMapper) {
        this.walletMapper = walletMapper;

        this.userWalletRepository = userWalletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.walletWithdrawalRepository = walletWithdrawalRepository;
        this.withdrawalHistoryRepository = withdrawalHistoryRepository;
        this.reportEvidenceStorage = reportEvidenceStorage;
    }

    @Override
    public UserWallet getWalletByUserId(UUID userId) {
        return userWalletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletForUser(userId));
    }

    @Override
    @Transactional
    public UserWallet createWalletForUser(UUID userId) {
        if (userWalletRepository.findByUserId(userId).isPresent()) {
            return userWalletRepository.findByUserId(userId).get();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserWallet wallet = new UserWallet();
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);

        return userWalletRepository.save(wallet);
    }

    @Override
    @Transactional
    public void deposit(UUID userId, BigDecimal amount, UUID referenceId) {
        validateAmountPrecision(amount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Deposit amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        saveChangedWallet(wallet);

        createTransaction(wallet, amount, WalletTransactionType.DEPOSIT, referenceId);

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                userId,
                "Nạp tiền vào ví thành công: +" + amount + " VND",
                "Bạn đã nạp thành công " + amount + " VND vào ví SecondLife.",
                NotificationType.WALLET,
                referenceId,
                "WALLET_TRANSACTION",
                "/wallet"
            );
        }
    }

    @Override
    @Transactional
    public void processPayment(UUID userId, BigDecimal amount, UUID orderId) {
        processPayment(userId, amount, orderId, WalletTransactionReferenceType.ORDER);
    }

    @Override
    @Transactional
    public void processPayment(UUID userId, BigDecimal amount, UUID orderId, WalletTransactionReferenceType referenceType) {
        validateAmountPrecision(amount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        saveChangedWallet(wallet);

        // Record a negative transaction for payment
        createTransaction(wallet, amount.negate(), WalletTransactionType.PAYMENT, orderId, referenceType);
    }

    @Override
    @Transactional
    public void processEarning(UUID userId, BigDecimal amount, UUID orderId) {
        processEarning(userId, amount, orderId, WalletTransactionReferenceType.ORDER);
    }

    @Override
    @Transactional
    public void processEarning(UUID userId, BigDecimal amount, UUID orderId, WalletTransactionReferenceType referenceType) {
        validateAmountPrecision(amount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Earning amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        saveChangedWallet(wallet);

        createTransaction(wallet, amount, WalletTransactionType.EARNING, orderId, referenceType);

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                userId,
                "Giải ngân tiền bán hàng: +" + amount + " VND",
                "Bạn đã nhận được " + amount + " VND doanh thu bán hàng vào ví SecondLife.",
                NotificationType.WALLET,
                orderId,
                "ORDER",
                "/wallet"
            );
        }
    }

    @Override
    @Transactional
    public void processRefund(UUID userId, BigDecimal amount, UUID orderId) {
        processRefund(userId, amount, orderId, WalletTransactionReferenceType.ORDER);
    }

    @Override
    @Transactional
    public void processRefund(UUID userId, BigDecimal amount, UUID orderId, WalletTransactionReferenceType referenceType) {
        validateAmountPrecision(amount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        saveChangedWallet(wallet);

        createTransaction(wallet, amount, WalletTransactionType.REFUND, orderId, referenceType);

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                userId,
                "Hoàn tiền vào ví: +" + amount + " VND",
                "Bạn đã nhận được khoản hoàn tiền " + amount + " VND vào ví SecondLife cho đơn hàng.",
                NotificationType.WALLET,
                orderId,
                "ORDER",
                "/wallet"
            );
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void reimburseShipping(UUID userId, BigDecimal amount, UUID shipmentId) {
        validateAmountPrecision(amount);
        if (amount.signum() <= 0) {
            throw new BadRequestException("Shipping reimbursement must be positive");
        }
        userWalletRepository.ensureWalletExists(UUID.randomUUID(), userId);
        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        saveChangedWallet(wallet);
        createTransaction(wallet, amount, WalletTransactionType.SHIPPING_REIMBURSE, shipmentId,
                WalletTransactionReferenceType.SHIPMENT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal collectReturnShippingFee(UUID sellerId, BigDecimal fee, UUID shipmentId) {
        if (fee == null || fee.signum() < 0 || fee.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new ConflictException("Invalid return shipping fee");
        }
        return collectAvailableFee(sellerId, fee, shipmentId, WalletTransactionType.RETURN_SHIPPING_FEE,
                WalletTransactionReferenceType.SHIPMENT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal collectFailedInspectionFee(UUID sellerId, BigDecimal fee, UUID inspectionOrderId) {
        return collectAvailableFee(sellerId, fee, inspectionOrderId, WalletTransactionType.PAYMENT,
                WalletTransactionReferenceType.INSPECTION_ORDER);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal collectInspectionReturnFee(UUID userId, BigDecimal fee, UUID inspectionOrderId) {
        if (fee == null || fee.signum() < 0) throw new ConflictException("Invalid inspection return fee");
        return collectAvailableFee(userId, fee, inspectionOrderId, WalletTransactionType.PAYMENT,
                WalletTransactionReferenceType.INSPECTION_ORDER);
    }

    private BigDecimal collectAvailableFee(UUID sellerId, BigDecimal fee, UUID referenceId,
                                           WalletTransactionType type, WalletTransactionReferenceType referenceType) {
        BigDecimal amount = fee.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() == 0) return amount;

        // A seller may never have received an earning. Create the empty wallet safely under concurrent first use.
        userWalletRepository.ensureWalletExists(UUID.randomUUID(), sellerId);
        UserWallet wallet = userWalletRepository.findByUserIdWithLock(sellerId).orElseThrow();
        if (wallet.getBalance().signum() < 0) throw new ConflictException("Seller wallet balance is inconsistent");
        BigDecimal collected = wallet.getBalance().min(amount);
        if (collected.signum() > 0) {
            wallet.setBalance(wallet.getBalance().subtract(collected));
            saveChangedWallet(wallet);
            createTransaction(wallet, collected.negate(), type, referenceId, referenceType);
        }
        return collected;
    }

    private UserWallet getWalletWithLock(UUID userId) {
        return userWalletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    // Create if not exists, then lock
                    createWalletForUser(userId);
                    return userWalletRepository.findByUserIdWithLock(userId).orElseThrow();
                });
    }

    private void validateAmountPrecision(BigDecimal amount) {
        if (amount == null) throw new BadRequestException("Wallet amount is required");
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("Wallet amount must have at most 2 decimal places");
        }
    }

    private void createTransaction(UserWallet wallet, BigDecimal amount, WalletTransactionType type, UUID referenceId) {
        WalletTransactionReferenceType referenceType = switch (type) {
            case DEPOSIT -> WalletTransactionReferenceType.DEPOSIT_REQUEST;
            case WITHDRAW -> WalletTransactionReferenceType.WITHDRAWAL;
            case RETURN_SHIPPING_FEE -> WalletTransactionReferenceType.SHIPMENT;
            default -> WalletTransactionReferenceType.UNKNOWN;
        };
        createTransaction(wallet, amount, type, referenceId, referenceType);
    }

    private void createTransaction(UserWallet wallet, BigDecimal amount, WalletTransactionType type, UUID referenceId,
                                   WalletTransactionReferenceType referenceType) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setReferenceId(referenceId);
        transaction.setReferenceType(referenceType != null ? referenceType : WalletTransactionReferenceType.UNKNOWN);
        transaction.setBalanceAfter(wallet.getBalance());
        transaction.setBalanceBefore(wallet.getBalance().subtract(amount));
        walletTransactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransaction> getTransactions(UUID userId, Pageable pageable) {
        UserWallet wallet = getWalletByUserId(userId);
        return walletTransactionRepository.findByWalletId(wallet.getId(), pageable);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse requestWithdrawal(UUID userId, WalletWithdrawalRequest request) {
        return requestWithdrawal(userId, request, null, true);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse requestWithdrawal(UUID userId, WalletWithdrawalRequest request,
                                                     String idempotencyKey, boolean bankInfoConfirmed) {
        return requestWithdrawal(userId, request, idempotencyKey, bankInfoConfirmed, bankWarningVersion);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse requestWithdrawal(UUID userId, WalletWithdrawalRequest request,
                                                     String idempotencyKey, boolean bankInfoConfirmed, String warningVersion) {
        if (walletWithdrawalRepository == null) {
            throw new IllegalStateException("WalletWithdrawalRepository is not available");
        }
        validateAmountPrecision(request.amount());
        if (request.amount() != null && request.amount().stripTrailingZeros().scale() > 0) {
            throw new BadRequestException("Số tiền rút phải là số nguyên VND");
        }
        if (request.amount() == null || request.amount().compareTo(new BigDecimal("10000")) < 0) {
            throw new BadRequestException("Số tiền rút tối thiểu là 10.000 VND");
        }
        if (!bankInfoConfirmed || warningVersion == null || !bankWarningVersion.equals(warningVersion)) {
            throw new BadRequestException("Vui lòng xác nhận chính xác thông tin ngân hàng trước khi gửi yêu cầu");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = idempotencyKey.trim();
            if (key.length() > 100) throw new BadRequestException("Idempotency key is too long");
            var existing = walletWithdrawalRepository.findByUserIdAndIdempotencyKey(userId, key);
            if (existing.isPresent()) {
                WalletWithdrawal previous = existing.get();
                if (!sameWithdrawalPayload(previous, request)) {
                    throw new ConflictException("Idempotency key was already used with different withdrawal details");
                }
                return walletMapper.toWithdrawalResponse(previous);
            }
        }

        UserWallet wallet = getWalletWithLock(userId);
        if (!wallet.isActive()) {
            throw new BadRequestException("Ví của bạn đang tạm khóa hoặc đóng băng; không thể tạo yêu cầu rút tiền");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = walletWithdrawalRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey.trim());
            if (existing.isPresent()) {
                if (!sameWithdrawalPayload(existing.get(), request)) throw new ConflictException("Idempotency key was already used with different withdrawal details");
                return walletMapper.toWithdrawalResponse(existing.get(), wallet);
            }
        }
        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Số dư khả dụng không đủ để thực hiện rút tiền");
        }
        if (maxPendingWithdrawals > 0 && walletWithdrawalRepository.countByUserIdAndStatus(userId, "PENDING") >= maxPendingWithdrawals) {
            throw new BadRequestException("Bạn chỉ có thể có tối đa " + maxPendingWithdrawals + " yêu cầu rút tiền đang chờ xử lý cùng lúc");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getAccountStatus() != null && user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Tài khoản đang bị tạm khóa hoặc chưa hoạt động; không thể thực hiện rút tiền");
        }

        // balance is spendable funds; a withdrawal moves the same amount to reserved funds.
        wallet.setBalance(wallet.getBalance().subtract(request.amount()));
        wallet.setReservedBalance(zeroIfNull(wallet.getReservedBalance()).add(request.amount()));
        saveChangedWallet(wallet);

        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user,
                request.amount(),
                request.bankCode().trim().toUpperCase(),
                request.accountNumber().trim(),
                request.accountHolderName().trim().toUpperCase()
        );
        Instant now = Instant.now();
        withdrawal.setBankName(request.bankName() == null || request.bankName().isBlank()
                ? request.bankCode().trim().toUpperCase() : request.bankName().trim());
        withdrawal.setRequestCode("SLWD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        withdrawal.setBankInfoConfirmedAt(now);
        withdrawal.setBankInfoWarningVersion(warningVersion);
        withdrawal.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim());
        withdrawal = walletWithdrawalRepository.save(withdrawal);
        recordWithdrawalStatus(withdrawal, null, "PENDING", user, "REQUESTED", null);

        createTransaction(wallet, request.amount().negate(), WalletTransactionType.WITHDRAW, withdrawal.getId());

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                userId,
                "Yêu cầu rút tiền đang chờ xử lý",
                "Yêu cầu rút " + request.amount() + " VND về ngân hàng " + request.bankCode() + " đang chờ nhân viên tiếp nhận.",
                NotificationType.WALLET,
                withdrawal.getId(),
                "WALLET_WITHDRAWAL",
                "/wallet/withdrawals/" + withdrawal.getId()
            );
        }

        return walletMapper.toWithdrawalResponse(withdrawal, wallet);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse reviewWithdrawal(UUID adminId, UUID withdrawalId, WalletWithdrawalReviewRequest request) {
        if (walletWithdrawalRepository == null) {
            throw new IllegalStateException("WalletWithdrawalRepository is not available");
        }

        WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu rút tiền với mã: " + withdrawalId));

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin user not found"));
        String action = request.action().trim().toUpperCase();
        String oldStatus = withdrawal.getStatus();
        Instant now = Instant.now();
        withdrawal.setUpdatedAt(now);
        if (request.adminNote() != null && !request.adminNote().isBlank()) withdrawal.setAdminNote(request.adminNote().trim());

        UserWallet touchedWallet = null;

        switch (action) {
            case "CLAIM", "ACCEPT" -> {
                if (!("PENDING".equals(withdrawal.getStatus()) || "TRANSFER_FAILED".equals(withdrawal.getStatus())))
                    throw new BadRequestException("Chỉ yêu cầu chờ tiếp nhận hoặc chuyển khoản thất bại mới có thể được nhận xử lý");
                withdrawal.setStatus("PROCESSING");
                withdrawal.setAcceptedBy(admin);
                withdrawal.setAcceptedAt(now);
            }
            case "UNCLAIM" -> {
                requireStatus(withdrawal, "PROCESSING");
                assertAssignedStaffOrAdmin(withdrawal, admin);
                withdrawal.setStatus("PENDING");
                withdrawal.setAcceptedBy(null);
                withdrawal.setAcceptedAt(null);
            }
            case "REASSIGN" -> {
                requireStatus(withdrawal, "PROCESSING");
                if (!admin.hasRole("ADMIN")) {
                    throw new ConflictException("Chỉ quản trị viên mới có quyền điều phối lại nhân viên xử lý");
                }
                if (request.targetStaffId() == null) {
                    throw new BadRequestException("Vui lòng chọn nhân viên nhận bàn giao");
                }
                User targetStaff = userRepository.findById(request.targetStaffId())
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy nhân viên được bàn giao"));
                withdrawal.setAcceptedBy(targetStaff);
                withdrawal.setAcceptedAt(now);
            }
            case "APPROVE", "CONFIRM_TRANSFER" -> {
                requireStatus(withdrawal, "PROCESSING");
                assertAssignedStaffOrAdmin(withdrawal, admin);
                if (withdrawal.getEvidenceStorageId() == null || withdrawal.getEvidenceUrl() == null
                        || !withdrawal.getEvidenceUrl().matches("/api/v1/wallets/withdrawals/evidence/[0-9a-fA-F-]{36}"))
                    throw new BadRequestException("Tải ảnh minh chứng giao dịch thành công lên trước khi xác nhận chuyển khoản");
                UserWallet wallet = getWalletWithLock(withdrawal.getUser().getId());
                BigDecimal reserved = zeroIfNull(wallet.getReservedBalance());
                if (reserved.compareTo(withdrawal.getAmount()) < 0) throw new ConflictException("Wallet reserved balance is inconsistent");
                wallet.setReservedBalance(reserved.subtract(withdrawal.getAmount()));
                saveChangedWallet(wallet);
                touchedWallet = wallet;
                withdrawal.setTransferredBy(admin); withdrawal.setTransferredAt(now);
                withdrawal.setNotificationAt(now); withdrawal.setDisputeDeadline(now.plusSeconds(48L * 60 * 60));
                withdrawal.setReviewedBy(admin); withdrawal.setReviewedAt(now); withdrawal.setStatus("TRANSFERRED_AWAITING_REVIEW");
            }
            case "REJECT", "CANCEL" -> {
                if (!("PENDING".equals(withdrawal.getStatus()) || "PROCESSING".equals(withdrawal.getStatus()) || "TRANSFER_FAILED".equals(withdrawal.getStatus())))
                    throw new BadRequestException("Yêu cầu chỉ có thể bị từ chối trước khi chuyển khoản thành công");
                if ("PROCESSING".equals(withdrawal.getStatus())) {
                    assertAssignedStaffOrAdmin(withdrawal, admin);
                }
                touchedWallet = releaseWithdrawalReservation(withdrawal);
                withdrawal.setReviewedBy(admin); withdrawal.setReviewedAt(now); withdrawal.setStatus("REJECTED");
            }
            case "TRANSFER_FAILED" -> {
                requireStatus(withdrawal, "PROCESSING");
                assertAssignedStaffOrAdmin(withdrawal, admin);
                clearWithdrawalEvidence(withdrawal);
                withdrawal.setStatus("TRANSFER_FAILED");
                withdrawal.setReviewedBy(admin);
                withdrawal.setReviewedAt(now);
            }
            case "START_DISPUTE_REVIEW" -> {
                requireStatus(withdrawal, "DISPUTED");
                withdrawal.setStatus("DISPUTE_PROCESSING");
                withdrawal.setReviewedBy(admin);
                withdrawal.setReviewedAt(now);
            }
            case "RESOLVE_DISPUTE" -> {
                if (!("DISPUTED".equals(withdrawal.getStatus()) || "DISPUTE_PROCESSING".equals(withdrawal.getStatus()))) {
                    throw new ConflictException("Withdrawal request status changed; current status is " + withdrawal.getStatus());
                }
                String outcome = normalizeDisputeResolutionOutcome(request.disputeResolutionOutcome());
                withdrawal.setDisputeResolutionOutcome(outcome);
                withdrawal.setDisputeResolvedBy(admin);
                withdrawal.setDisputeResolution(request.adminNote());
                String resEvidence = firstNonBlank(request.evidenceUrl(),
                        request.evidenceUrls() != null && !request.evidenceUrls().isEmpty() ? request.evidenceUrls().get(0) : null);
                if (resEvidence != null) {
                    withdrawal.setDisputeResolutionEvidenceUrl(resEvidence);
                }
                withdrawal.setReviewedBy(admin);
                withdrawal.setReviewedAt(now);

                if ("RESOLVED_REFUND_TO_WALLET".equals(outcome)) {
                    UserWallet wallet = getWalletWithLock(withdrawal.getUser().getId());
                    wallet.setBalance(wallet.getBalance().add(withdrawal.getAmount()));
                    saveChangedWallet(wallet);
                    createTransaction(wallet, withdrawal.getAmount(), WalletTransactionType.REFUND, withdrawal.getId(), WalletTransactionReferenceType.WITHDRAWAL);
                    touchedWallet = wallet;
                    withdrawal.setStatus("DISPUTE_RESOLVED");
                } else if ("RESOLVED_RETRY_TRANSFER".equals(outcome)) {
                    UserWallet wallet = getWalletWithLock(withdrawal.getUser().getId());
                    wallet.setReservedBalance(zeroIfNull(wallet.getReservedBalance()).add(withdrawal.getAmount()));
                    saveChangedWallet(wallet);
                    touchedWallet = wallet;
                    clearWithdrawalEvidence(withdrawal);
                    withdrawal.setStatus("TRANSFER_FAILED");
                } else {
                    withdrawal.setStatus("DISPUTE_RESOLVED");
                }
            }
            default -> throw new BadRequestException("Hành động không hợp lệ: " + action);
        }

        withdrawal = walletWithdrawalRepository.save(withdrawal);
        recordWithdrawalStatus(withdrawal, oldStatus, withdrawal.getStatus(), admin, action, request.adminNote());
        sendReviewNotifications(withdrawal, action, request.adminNote());
        return enrichWithdrawalResponse(withdrawal, touchedWallet);
    }

    private void sendReviewNotifications(WalletWithdrawal withdrawal, String action, String adminNote) {
        if (notificationService == null) {
            return;
        }
        String targetUrl = "/wallet/withdrawals/" + withdrawal.getId();
        UUID ownerId = withdrawal.getUser().getId();
        if ("TRANSFERRED_AWAITING_REVIEW".equals(withdrawal.getStatus())) {
            var notification = notificationService.notifyInApp(ownerId, "Yêu cầu rút tiền đã được chuyển khoản",
                    "Yêu cầu " + withdrawal.getRequestCode() + " đã được xác nhận chuyển khoản. Vui lòng kiểm tra tài khoản ngân hàng; bạn có thể báo cáo vấn đề trước " + withdrawal.getDisputeDeadline() + ".",
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", targetUrl);
            if (notification != null && notification.createdAt() != null) {
                withdrawal.setNotificationAt(notification.createdAt());
                withdrawal.setDisputeDeadline(notification.createdAt().plusSeconds(48L * 60 * 60));
                walletWithdrawalRepository.save(withdrawal);
            }
        } else if ("REJECTED".equals(withdrawal.getStatus())) {
            notificationService.notifyInApp(ownerId, "Yêu cầu rút tiền đã bị từ chối",
                    "Yêu cầu " + withdrawal.getRequestCode() + " đã bị từ chối. Số tiền đã được trả lại vào số dư khả dụng. Lý do: " + (adminNote == null ? "Không có lý do cụ thể" : adminNote),
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", targetUrl);
        } else if ("PROCESSING".equals(withdrawal.getStatus()) && ("CLAIM".equals(action) || "ACCEPT".equals(action))) {
            notificationService.notifyInApp(ownerId, "Yêu cầu rút tiền đang được xử lý",
                    "Nhân viên đã tiếp nhận yêu cầu rút tiền " + withdrawal.getRequestCode() + " và đang tiến hành đối chiếu chuyển khoản.",
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", targetUrl);
        } else if ("TRANSFER_FAILED".equals(withdrawal.getStatus())) {
            String msg = "RESOLVE_DISPUTE".equals(action)
                    ? "Báo cáo cho lệnh " + withdrawal.getRequestCode() + " đã được xác nhận chuyển khoản lại. Nhân viên sẽ sớm thực hiện lại giao dịch."
                    : "Chuyển khoản cho lệnh " + withdrawal.getRequestCode() + " chưa hoàn tất. Số tiền vẫn đang được giữ an toàn để nhân viên kiểm tra lại." + (adminNote != null && !adminNote.isBlank() ? " Ghi chú: " + adminNote : "");
            notificationService.notifyInApp(ownerId, "Chuyển khoản rút tiền chưa hoàn tất",
                    msg, NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", targetUrl);
        } else if ("DISPUTE_PROCESSING".equals(withdrawal.getStatus())) {
            notificationService.notifyInApp(ownerId, "Báo cáo vấn đề rút tiền đang được xác minh",
                    "Nhân viên đã tiếp nhận báo cáo vấn đề cho lệnh rút tiền " + withdrawal.getRequestCode() + " và đang kiểm tra giao dịch ngân hàng.",
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", targetUrl);
        } else if ("DISPUTE_RESOLVED".equals(withdrawal.getStatus())) {
            String outcomeText = switch (String.valueOf(withdrawal.getDisputeResolutionOutcome())) {
                case "RESOLVED_REFUND_TO_WALLET" -> "Số tiền rút đã được hoàn lại vào số dư khả dụng của ví.";
                default -> "Giao dịch chuyển khoản đã được xác minh là hợp lệ.";
            };
            notificationService.notifyInApp(ownerId, "Báo cáo vấn đề rút tiền đã có kết quả",
                    "Báo cáo cho lệnh " + withdrawal.getRequestCode() + " đã được giải quyết. " + outcomeText + (adminNote != null && !adminNote.isBlank() ? " Kết luận: " + adminNote : ""),
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", targetUrl);
        }
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse reportWithdrawalIssue(UUID userId, UUID withdrawalId, String type, String description) {
        return reportWithdrawalIssue(userId, withdrawalId, type, description, java.util.List.of());
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse reportWithdrawalIssue(UUID userId, UUID withdrawalId, String type, String description, java.util.List<String> evidenceUrls) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        if (!withdrawal.getUser().getId().equals(userId)) throw new NotFoundException("Withdrawal request not found");
        Instant now = Instant.now();
        if (!"TRANSFERRED_AWAITING_REVIEW".equals(withdrawal.getStatus()) || withdrawal.getDisputeDeadline() == null || now.isAfter(withdrawal.getDisputeDeadline()))
            throw new BadRequestException("Thời hạn báo cáo vấn đề đã kết thúc hoặc yêu cầu không ở trạng thái đối soát");
        if (type == null || type.isBlank() || description == null || description.isBlank())
            throw new BadRequestException("Vui lòng chọn loại vấn đề và mô tả chi tiết");
        String old = withdrawal.getStatus();
        withdrawal.setStatus("DISPUTED");
        withdrawal.setDisputeType(type.trim());
        withdrawal.setDisputeDescription(description.trim());
        withdrawal.setDisputeEvidenceUrls(joinEvidenceUrls(evidenceUrls));
        withdrawal.setDisputedAt(now);
        withdrawal.setUpdatedAt(now);
        withdrawal = walletWithdrawalRepository.save(withdrawal);
        recordWithdrawalStatus(withdrawal, old, "DISPUTED", withdrawal.getUser(), "USER_DISPUTE", type.trim());
        if (notificationService != null) {
            notificationService.notifyInApp(userId, "Đã ghi nhận báo cáo rút tiền",
                    "Báo cáo cho yêu cầu " + withdrawal.getRequestCode() + " đã được ghi nhận để nhân viên xem xét.",
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + withdrawal.getId());
            notifyAssignedStaffForWithdrawal(withdrawal,
                    "Có báo cáo vấn đề cho lệnh rút tiền " + withdrawal.getRequestCode(),
                    "Người dùng đã báo cáo vấn đề (" + type.trim() + ") cho lệnh rút tiền " + withdrawal.getRequestCode() + ". Vui lòng kiểm tra và xác minh.");
        }
        return enrichWithdrawalResponse(withdrawal, null);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse requestWithdrawalException(UUID userId, UUID withdrawalId, String reason, java.util.List<String> evidenceUrls) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        if (!withdrawal.getUser().getId().equals(userId)) {
            throw new NotFoundException("Withdrawal request not found");
        }
        if (!"COMPLETED".equals(withdrawal.getStatus())) {
            throw new BadRequestException("Yêu cầu hỗ trợ ngoại lệ chỉ áp dụng cho giao dịch rút tiền đã hoàn tất");
        }
        if (withdrawal.getExceptionRequestedAt() != null) {
            throw new ConflictException("Bạn đã gửi yêu cầu hỗ trợ ngoại lệ cho giao dịch rút tiền này trước đó");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Vui lòng mô tả chi tiết lý do cần hỗ trợ ngoại lệ");
        }
        Instant now = Instant.now();
        withdrawal.setExceptionRequestedAt(now);
        withdrawal.setExceptionReason(reason.trim());
        withdrawal.setExceptionEvidenceUrls(joinEvidenceUrls(evidenceUrls));
        withdrawal.setUpdatedAt(now);
        withdrawal = walletWithdrawalRepository.save(withdrawal);
        recordWithdrawalStatus(withdrawal, "COMPLETED", "COMPLETED", withdrawal.getUser(), "EXCEPTION_REQUESTED", reason.trim());
        if (notificationService != null) {
            notificationService.notifyInApp(userId, "Đã ghi nhận yêu cầu hỗ trợ ngoại lệ",
                    "Yêu cầu hỗ trợ ngoại lệ cho lệnh rút tiền " + withdrawal.getRequestCode() + " đã được ghi nhận để Admin/Manager xem xét. Giao dịch không tự động hoàn tiền trong thời gian xác minh.",
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + withdrawal.getId());
            notifyAssignedStaffForWithdrawal(withdrawal,
                    "Có yêu cầu hỗ trợ ngoại lệ sau 48h cho lệnh " + withdrawal.getRequestCode(),
                    "Người dùng gửi yêu cầu ngoại lệ sau hoàn tất cho lệnh " + withdrawal.getRequestCode() + ". Lý do: " + reason.trim());
        }
        return enrichWithdrawalResponse(withdrawal, null);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse cancelWithdrawal(UUID userId, UUID withdrawalId, String reason) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        if (!withdrawal.getUser().getId().equals(userId)) throw new NotFoundException("Withdrawal request not found");
        requireStatus(withdrawal, "PENDING");
        String old = withdrawal.getStatus();
        UserWallet wallet = releaseWithdrawalReservation(withdrawal);
        withdrawal.setStatus("CANCELLED"); withdrawal.setAdminNote(reason == null || reason.isBlank() ? "Cancelled by owner" : reason.trim());
        withdrawal.setUpdatedAt(Instant.now());
        withdrawal = walletWithdrawalRepository.save(withdrawal);
        recordWithdrawalStatus(withdrawal, old, "CANCELLED", withdrawal.getUser(), "USER_CANCELLED", withdrawal.getAdminNote());
        if (notificationService != null) notificationService.notifyInApp(userId, "Yêu cầu rút tiền đã được hủy",
                "Yêu cầu " + withdrawal.getRequestCode() + " đã được hủy; số tiền đã trở lại số dư khả dụng.",
                NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + withdrawal.getId());
        return enrichWithdrawalResponse(withdrawal, wallet);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse confirmReceipt(UUID userId, UUID id) {
        var w = walletWithdrawalRepository.findByIdForUpdate(id)
                .filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        String oldStatus = w.getStatus();
        Instant now = Instant.now();
        boolean closingDispute = "DISPUTED".equals(oldStatus) || "DISPUTE_PROCESSING".equals(oldStatus);
        if ("TRANSFERRED_AWAITING_REVIEW".equals(oldStatus)) {
            if (w.getDisputeDeadline() != null && !now.isBefore(w.getDisputeDeadline())) {
                throw new ConflictException("Đã hết hạn đối soát");
            }
        } else if (!closingDispute) {
            throw new ConflictException("Withdrawal request status changed; current status is " + oldStatus);
        }

        w.setStatus("COMPLETED");
        w.setCompletedAt(now);
        w.setUpdatedAt(now);
        if (closingDispute) {
            w.setDisputeResolutionOutcome("RESOLVED_VALID_TRANSFER");
            w.setDisputeResolution("Người dùng đã nhận được tiền và chủ động đóng báo cáo sự cố");
        }
        walletWithdrawalRepository.save(w);
        recordWithdrawalStatus(
                w,
                oldStatus,
                "COMPLETED",
                w.getUser(),
                closingDispute ? "OWNER_CLOSED_DISPUTE_CONFIRMED" : "OWNER_CONFIRMED",
                closingDispute ? "Người dùng xác nhận đã nhận đủ tiền và đóng báo cáo sự cố" : null
        );
        if (notificationService != null) {
            notificationService.notifyInApp(userId, "Yêu cầu rút tiền đã hoàn tất",
                    closingDispute
                            ? "Bạn đã đóng báo cáo sự cố và xác nhận nhận đủ tiền cho lệnh rút tiền " + w.getRequestCode() + "."
                            : "Bạn đã xác nhận nhận đủ tiền cho lệnh rút tiền " + w.getRequestCode() + ".",
                    NotificationType.WALLET, w.getId(), "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + w.getId());
            if (closingDispute) {
                notifyAssignedStaffForWithdrawal(w,
                        "Người dùng đã đóng báo cáo cho lệnh " + w.getRequestCode(),
                        "Chủ ví đã xác nhận nhận được tiền và chủ động đóng báo cáo sự cố cho lệnh rút tiền " + w.getRequestCode() + ".");
            }
        }
        return enrichWithdrawalResponse(w, null);
    }

    @Override
    @Transactional
    public int completeExpiredWithdrawals() {
        if (walletWithdrawalRepository == null) return 0;
        for (UUID id : walletWithdrawalRepository.lockDueReminderIds()) {
            var w = walletWithdrawalRepository.findById(id).orElseThrow();
            var left = java.time.Duration.between(Instant.now(), w.getDisputeDeadline());
            if (left.isZero() || left.isNegative()) continue;
            int milestone = left.compareTo(java.time.Duration.ofHours(1)) <= 0 ? 1 :
                    left.compareTo(java.time.Duration.ofHours(6)) <= 0 ? 6 :
                    left.compareTo(java.time.Duration.ofHours(12)) <= 0 ? 12 : 24;
            if (notificationService != null) {
                notificationService.notifyInApp(w.getUser().getId(), "Bạn đã nhận được tiền chưa?",
                        "Lệnh " + w.getRequestCode() + " còn " + Math.max(1, left.toMinutes()) +
                        " phút để xác nhận đã nhận hoặc báo cáo lỗi.", NotificationType.WALLET, id,
                        "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + id);
            }
            w.setReminderHours(milestone);
        }
        var due = walletWithdrawalRepository.findByDisputeDeadlineBeforeAndStatus(Instant.now(), "TRANSFERRED_AWAITING_REVIEW", Pageable.ofSize(200));
        int count = 0;
        for (WalletWithdrawal candidate : due) {
            WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (withdrawal == null || !"TRANSFERRED_AWAITING_REVIEW".equals(withdrawal.getStatus())
                    || withdrawal.getDisputeDeadline() == null || withdrawal.getDisputeDeadline().isAfter(Instant.now())) continue;
            String old = withdrawal.getStatus();
            withdrawal.setStatus("COMPLETED"); withdrawal.setCompletedAt(Instant.now()); withdrawal.setUpdatedAt(Instant.now());
            walletWithdrawalRepository.save(withdrawal);
            recordWithdrawalStatus(withdrawal, old, "COMPLETED", null, "AUTO_COMPLETE", null);
            if (notificationService != null) {
                notificationService.notifyInApp(withdrawal.getUser().getId(), "Yêu cầu rút tiền đã hoàn tất",
                        "Lệnh rút tiền " + withdrawal.getRequestCode() + " đã tự động chuyển sang hoàn tất sau 48 giờ đối soát.",
                        NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + withdrawal.getId());
            }
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse uploadWithdrawalEvidence(UUID staffId, UUID withdrawalId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Ảnh minh chứng đang trống");
        long maxBytes = maxEvidenceFileSizeBytes > 0 ? maxEvidenceFileSizeBytes : 10L * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("Ảnh minh chứng không được vượt quá " + formatEvidenceSizeLimit(maxBytes));
        }
        if (!java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(String.valueOf(file.getContentType())))
            throw new BadRequestException("Chỉ chấp nhận ảnh JPEG, PNG hoặc WEBP");
        if (reportEvidenceStorage == null) throw new IllegalStateException("Private evidence storage is not configured");
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        if (!"PROCESSING".equals(withdrawal.getStatus())) throw new ConflictException("Evidence can only be attached to a processing withdrawal");
        User staff = userRepository.findById(staffId).orElseThrow(() -> new NotFoundException("Staff not found"));
        if (withdrawal.getAcceptedBy() == null || (!withdrawal.getAcceptedBy().getId().equals(staffId) && !staff.hasRole("ADMIN")))
            throw new ConflictException("Only the assigned staff member may upload transfer evidence");
        try {
            var stored = reportEvidenceStorage.upload(file);
            withdrawal.setEvidenceStorageId(stored.storageId());
            withdrawal.setEvidenceContentType(stored.contentType());
            withdrawal.setEvidenceUploadedBy(staff);
            withdrawal.setEvidenceUploadedAt(Instant.now());
            withdrawal.setEvidenceUrl("/api/v1/wallets/withdrawals/evidence/" + withdrawal.getId());
            withdrawal.setUpdatedAt(Instant.now());
            return enrichWithdrawalResponse(walletWithdrawalRepository.save(withdrawal), null);
        } catch (IOException ex) {
            throw new BadRequestException("Tải ảnh minh chứng lên kho riêng tư thất bại; vui lòng thử lại");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReportEvidenceContent readWithdrawalEvidence(UUID actorId, boolean staff, UUID withdrawalId) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        if (!staff && !withdrawal.getUser().getId().equals(actorId)) throw new NotFoundException("Withdrawal request not found");
        if (withdrawal.getEvidenceStorageId() == null) throw new NotFoundException("Withdrawal evidence not found");
        try { return new ReportEvidenceContent(withdrawal.getEvidenceContentType(), reportEvidenceStorage.read(withdrawal.getEvidenceStorageId())); }
        catch (IOException ex) { throw new BadRequestException("Withdrawal evidence is temporarily unavailable"); }
    }

    @Override
    @Transactional(readOnly = true)
    public WalletWithdrawalResponse getMyWithdrawal(UUID userId, UUID withdrawalId) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(withdrawalId)
                .filter(row -> row.getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        return enrichWithdrawalResponse(withdrawal, null);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<WalletWithdrawalStatusHistoryResponse> getMyWithdrawalStatusHistory(UUID userId, UUID withdrawalId) {
        getMyWithdrawal(userId, withdrawalId);
        if (withdrawalHistoryRepository == null) return java.util.List.of();
        return withdrawalHistoryRepository.findByWithdrawalIdAndWithdrawalUserIdOrderByCreatedAtAsc(withdrawalId, userId).stream()
                .map(row -> new WalletWithdrawalStatusHistoryResponse(row.getOldStatus(), row.getNewStatus(),
                        row.getActor() == null ? null : row.getActor().getId(), row.getAction(), row.getReason(), row.getCreatedAt())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<WalletWithdrawalStatusHistoryResponse> getAdminWithdrawalStatusHistory(UUID withdrawalId) {
        walletWithdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu rút tiền với mã: " + withdrawalId));
        if (withdrawalHistoryRepository == null) return java.util.List.of();
        return withdrawalHistoryRepository.findByWithdrawalIdOrderByCreatedAtAsc(withdrawalId).stream()
                .map(row -> new WalletWithdrawalStatusHistoryResponse(row.getOldStatus(), row.getNewStatus(),
                        row.getActor() == null ? null : row.getActor().getId(), row.getAction(), row.getReason(), row.getCreatedAt())).toList();
    }

    private UserWallet releaseWithdrawalReservation(WalletWithdrawal withdrawal) {
        UserWallet wallet = getWalletWithLock(withdrawal.getUser().getId());
        BigDecimal reserved = zeroIfNull(wallet.getReservedBalance());
        if (reserved.compareTo(withdrawal.getAmount()) < 0) throw new ConflictException("Wallet reserved balance is inconsistent");
        wallet.setReservedBalance(reserved.subtract(withdrawal.getAmount()));
        wallet.setBalance(wallet.getBalance().add(withdrawal.getAmount()));
        saveChangedWallet(wallet);
        createTransaction(wallet, withdrawal.getAmount(), WalletTransactionType.REFUND, withdrawal.getId(), WalletTransactionReferenceType.WITHDRAWAL);
        return wallet;
    }

    private void assertAssignedStaffOrAdmin(WalletWithdrawal withdrawal, User actor) {
        if (withdrawal.getAcceptedBy() != null
                && !withdrawal.getAcceptedBy().getId().equals(actor.getId())
                && !actor.hasRole("ADMIN")) {
            throw new ConflictException("Yêu cầu rút tiền này đang được nhân viên khác tiếp nhận xử lý");
        }
    }

    private void clearWithdrawalEvidence(WalletWithdrawal withdrawal) {
        withdrawal.setEvidenceUrl(null);
        withdrawal.setEvidenceStorageId(null);
        withdrawal.setEvidenceContentType(null);
        withdrawal.setEvidenceUploadedAt(null);
        withdrawal.setEvidenceUploadedBy(null);
    }

    private static String formatEvidenceSizeLimit(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1.0) {
            if (Math.abs(mb - Math.rint(mb)) < 1e-6) {
                return String.format(java.util.Locale.ROOT, "%.0f MB", mb);
            }
            return String.format(java.util.Locale.ROOT, "%.1f MB", mb);
        }
        long kb = Math.max(1L, Math.round(bytes / 1024.0));
        return kb + " KB";
    }

    private String normalizeDisputeResolutionOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return "RESOLVED_VALID_TRANSFER";
        }
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "RESOLVED_VALID_TRANSFER", "RESOLVED_RETRY_TRANSFER", "RESOLVED_REFUND_TO_WALLET" -> upper;
            default -> throw new BadRequestException("Kết quả xử lý khiếu nại không hợp lệ: " + raw);
        };
    }

    private String joinEvidenceUrls(java.util.List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        java.util.List<String> cleaned = urls.stream()
                .filter(u -> u != null && !u.isBlank())
                .map(String::trim)
                .filter(u -> u.startsWith("http://") || u.startsWith("https://") || u.startsWith("/"))
                .distinct()
                .limit(5)
                .toList();
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    private void notifyAssignedStaffForWithdrawal(WalletWithdrawal withdrawal, String title, String message) {
        if (notificationService == null) {
            return;
        }
        java.util.Set<UUID> staffIds = new java.util.LinkedHashSet<>();
        if (withdrawal.getAcceptedBy() != null && withdrawal.getAcceptedBy().getId() != null) {
            staffIds.add(withdrawal.getAcceptedBy().getId());
        }
        if (withdrawal.getTransferredBy() != null && withdrawal.getTransferredBy().getId() != null) {
            staffIds.add(withdrawal.getTransferredBy().getId());
        }
        UUID ownerId = withdrawal.getUser() != null ? withdrawal.getUser().getId() : null;
        for (UUID staffId : staffIds) {
            if (!staffId.equals(ownerId)) {
                notificationService.notifyInApp(staffId, title, message,
                        NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/admin/withdrawals");
            }
        }
    }

    private WalletWithdrawalResponse enrichWithdrawalResponse(WalletWithdrawal withdrawal, UserWallet knownWallet) {
        UserWallet wallet = knownWallet;
        if (wallet == null && withdrawal != null && withdrawal.getUser() != null && withdrawal.getUser().getId() != null && userWalletRepository != null) {
            wallet = userWalletRepository.findByUserId(withdrawal.getUser().getId()).orElse(null);
        }
        return walletMapper.toWithdrawalResponse(withdrawal, wallet);
    }

    private void requireStatus(WalletWithdrawal withdrawal, String expected) {
        if (!expected.equals(withdrawal.getStatus())) throw new ConflictException("Withdrawal request status changed; current status is " + withdrawal.getStatus());
    }

    private void recordWithdrawalStatus(WalletWithdrawal withdrawal, String oldStatus, String newStatus, User actor, String action, String reason) {
        if (withdrawalHistoryRepository != null) withdrawalHistoryRepository.save(new WalletWithdrawalStatusHistory(withdrawal, oldStatus, newStatus, actor, action, reason));
    }

    private BigDecimal zeroIfNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private boolean sameWithdrawalPayload(WalletWithdrawal previous, WalletWithdrawalRequest request) {
        return previous.getAmount().compareTo(request.amount()) == 0
                && previous.getBankCode().equalsIgnoreCase(request.bankCode().trim())
                && normalizeBankName(previous.getBankName()).equals(normalizeBankName(request.bankName() == null || request.bankName().isBlank() ? request.bankCode() : request.bankName()))
                && previous.getAccountNumber().equals(request.accountNumber().trim())
                && previous.getAccountHolderName().equalsIgnoreCase(request.accountHolderName().trim());
    }

    private String normalizeBankName(String value) { return value == null ? "" : value.trim().toUpperCase(); }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletWithdrawalResponse> getMyWithdrawals(UUID userId, Pageable pageable) {
        if (walletWithdrawalRepository == null) {
            throw new IllegalStateException("WalletWithdrawalRepository is not available");
        }
        UserWallet wallet = userWalletRepository != null ? userWalletRepository.findByUserId(userId).orElse(null) : null;
        return walletWithdrawalRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(w -> walletMapper.toWithdrawalResponse(w, wallet));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletWithdrawalResponse> getAllWithdrawals(String status, Pageable pageable) {
        if (walletWithdrawalRepository == null) {
            throw new IllegalStateException("WalletWithdrawalRepository is not available");
        }
        Page<WalletWithdrawal> page = (status != null && !status.isBlank())
                ? walletWithdrawalRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pageable)
                : walletWithdrawalRepository.findAllByOrderByCreatedAtDesc(pageable);
        java.util.Map<UUID, UserWallet> walletCache = new java.util.HashMap<>();
        return page.map(w -> {
            UUID uid = w.getUser() != null ? w.getUser().getId() : null;
            UserWallet wallet = uid == null ? null : walletCache.computeIfAbsent(uid,
                    id -> userWalletRepository != null ? userWalletRepository.findByUserId(id).orElse(null) : null);
            return walletMapper.toWithdrawalResponse(w, wallet);
        });
    }
}

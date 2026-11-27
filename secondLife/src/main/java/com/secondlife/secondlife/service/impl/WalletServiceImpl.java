package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import com.secondlife.secondlife.entity.WalletWithdrawalStatusHistory;
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

    private final UserWalletRepository userWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final WalletWithdrawalRepository walletWithdrawalRepository;
    private final WalletWithdrawalStatusHistoryRepository withdrawalHistoryRepository;
    private final ReportEvidenceStorage reportEvidenceStorage;
    @Value("${app.wallet.withdrawal.bank-warning.version:withdrawal-bank-warning-v1}")
    private String bankWarningVersion = "withdrawal-bank-warning-v1";

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
        userWalletRepository.save(wallet);

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
        userWalletRepository.save(wallet);

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
        userWalletRepository.save(wallet);

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
        userWalletRepository.save(wallet);

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
        userWalletRepository.save(wallet);
        createTransaction(wallet, amount, WalletTransactionType.SHIPPING_REIMBURSE, shipmentId,
                WalletTransactionReferenceType.SHIPMENT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal collectReturnShippingFee(UUID sellerId, BigDecimal fee, UUID shipmentId) {
        if (fee == null || fee.signum() < 0 || fee.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new ConflictException("Invalid return shipping fee");
        }
        return collectSellerFee(sellerId, fee, shipmentId, WalletTransactionType.RETURN_SHIPPING_FEE,
                WalletTransactionReferenceType.SHIPMENT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal collectFailedInspectionFee(UUID sellerId, BigDecimal fee, UUID inspectionOrderId) {
        return collectSellerFee(sellerId, fee, inspectionOrderId, WalletTransactionType.PAYMENT,
                WalletTransactionReferenceType.INSPECTION_ORDER);
    }

    private BigDecimal collectSellerFee(UUID sellerId, BigDecimal fee, UUID referenceId,
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
            userWalletRepository.save(wallet);
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
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = walletWithdrawalRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey.trim());
            if (existing.isPresent()) {
                if (!sameWithdrawalPayload(existing.get(), request)) throw new ConflictException("Idempotency key was already used with different withdrawal details");
                return walletMapper.toWithdrawalResponse(existing.get());
            }
        }
        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Số dư khả dụng không đủ để thực hiện rút tiền");
        }

        // balance is spendable funds; a withdrawal moves the same amount to reserved funds.
        wallet.setBalance(wallet.getBalance().subtract(request.amount()));
        wallet.setReservedBalance(zeroIfNull(wallet.getReservedBalance()).add(request.amount()));
        userWalletRepository.save(wallet);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

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
                "/wallet"
            );
        }

        return walletMapper.toWithdrawalResponse(withdrawal);
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

        switch (action) {
            case "CLAIM", "ACCEPT" -> {
                if (!("PENDING".equals(withdrawal.getStatus()) || "TRANSFER_FAILED".equals(withdrawal.getStatus())))
                    throw new BadRequestException("Chỉ yêu cầu chờ tiếp nhận hoặc chuyển khoản thất bại mới có thể được nhận xử lý");
                withdrawal.setStatus("PROCESSING"); withdrawal.setAcceptedBy(admin); withdrawal.setAcceptedAt(now);
            }
            case "APPROVE", "CONFIRM_TRANSFER" -> {
                requireStatus(withdrawal, "PROCESSING");
                if (withdrawal.getEvidenceStorageId() == null || withdrawal.getEvidenceUrl() == null
                        || !withdrawal.getEvidenceUrl().matches("/api/v1/wallets/withdrawals/evidence/[0-9a-fA-F-]{36}"))
                    throw new BadRequestException("Tải ảnh minh chứng giao dịch thành công lên trước khi xác nhận chuyển khoản");
                UserWallet wallet = getWalletWithLock(withdrawal.getUser().getId());
                BigDecimal reserved = zeroIfNull(wallet.getReservedBalance());
                if (reserved.compareTo(withdrawal.getAmount()) < 0) throw new ConflictException("Wallet reserved balance is inconsistent");
                wallet.setReservedBalance(reserved.subtract(withdrawal.getAmount()));
                userWalletRepository.save(wallet);
                withdrawal.setTransferredBy(admin); withdrawal.setTransferredAt(now);
                withdrawal.setNotificationAt(now); withdrawal.setDisputeDeadline(now.plusSeconds(48L * 60 * 60));
                withdrawal.setReviewedBy(admin); withdrawal.setReviewedAt(now); withdrawal.setStatus("TRANSFERRED_AWAITING_REVIEW");
            }
            case "REJECT", "CANCEL" -> {
                if (!("PENDING".equals(withdrawal.getStatus()) || "PROCESSING".equals(withdrawal.getStatus()) || "TRANSFER_FAILED".equals(withdrawal.getStatus())))
                    throw new BadRequestException("Yêu cầu chỉ có thể bị từ chối trước khi chuyển khoản thành công");
                releaseWithdrawalReservation(withdrawal);
                withdrawal.setReviewedBy(admin); withdrawal.setReviewedAt(now); withdrawal.setStatus("REJECTED");
            }
            case "TRANSFER_FAILED" -> {
                requireStatus(withdrawal, "PROCESSING"); withdrawal.setStatus("TRANSFER_FAILED"); withdrawal.setReviewedBy(admin); withdrawal.setReviewedAt(now);
            }
            case "RESOLVE_DISPUTE" -> {
                requireStatus(withdrawal, "DISPUTED"); withdrawal.setStatus("DISPUTE_RESOLVED");
                withdrawal.setDisputeResolvedBy(admin); withdrawal.setDisputeResolution(request.adminNote());
            }
            default -> throw new BadRequestException("Hành động không hợp lệ: " + action);
        }

        withdrawal = walletWithdrawalRepository.save(withdrawal);
        recordWithdrawalStatus(withdrawal, oldStatus, withdrawal.getStatus(), admin, action, request.adminNote());
        if (notificationService != null && "TRANSFERRED_AWAITING_REVIEW".equals(withdrawal.getStatus())) {
            var notification = notificationService.notifyInApp(withdrawal.getUser().getId(), "Yêu cầu rút tiền đã được chuyển khoản",
                    "Yêu cầu " + withdrawal.getRequestCode() + " đã được xác nhận chuyển khoản. Vui lòng kiểm tra tài khoản ngân hàng; bạn có thể báo cáo vấn đề trước " + withdrawal.getDisputeDeadline() + ".",
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + withdrawal.getId());
            if (notification != null && notification.createdAt() != null) {
                withdrawal.setNotificationAt(notification.createdAt());
                withdrawal.setDisputeDeadline(notification.createdAt().plusSeconds(48L * 60 * 60));
                withdrawal = walletWithdrawalRepository.save(withdrawal);
            }
        } else if (notificationService != null && "REJECTED".equals(withdrawal.getStatus())) {
            notificationService.notifyInApp(withdrawal.getUser().getId(), "Yêu cầu rút tiền đã bị từ chối",
                    "Yêu cầu " + withdrawal.getRequestCode() + " đã bị từ chối. Số tiền đã được trả lại vào số dư khả dụng. Lý do: " + (request.adminNote() == null ? "Không có lý do cụ thể" : request.adminNote()),
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/wallet");
        }
        return walletMapper.toWithdrawalResponse(withdrawal);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse reportWithdrawalIssue(UUID userId, UUID withdrawalId, String type, String description) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        if (!withdrawal.getUser().getId().equals(userId)) throw new NotFoundException("Withdrawal request not found");
        Instant now = Instant.now();
        if (!"TRANSFERRED_AWAITING_REVIEW".equals(withdrawal.getStatus()) || withdrawal.getDisputeDeadline() == null || now.isAfter(withdrawal.getDisputeDeadline()))
            throw new BadRequestException("Thời hạn báo cáo vấn đề đã kết thúc hoặc yêu cầu không ở trạng thái đối soát");
        if (type == null || type.isBlank() || description == null || description.isBlank())
            throw new BadRequestException("Vui lòng chọn loại vấn đề và mô tả chi tiết");
        String old = withdrawal.getStatus();
        withdrawal.setStatus("DISPUTED"); withdrawal.setDisputeType(type.trim());
        withdrawal.setDisputeDescription(description.trim()); withdrawal.setDisputedAt(now); withdrawal.setUpdatedAt(now);
        withdrawal = walletWithdrawalRepository.save(withdrawal);
        recordWithdrawalStatus(withdrawal, old, "DISPUTED", withdrawal.getUser(), "USER_DISPUTE", type);
        if (notificationService != null) {
            notificationService.notifyInApp(userId, "Đã ghi nhận báo cáo rút tiền", "Báo cáo cho yêu cầu " + withdrawal.getRequestCode() + " đã được ghi nhận để nhân viên xem xét.",
                    NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + withdrawal.getId());
        }
        return walletMapper.toWithdrawalResponse(withdrawal);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse cancelWithdrawal(UUID userId, UUID withdrawalId, String reason) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        if (!withdrawal.getUser().getId().equals(userId)) throw new NotFoundException("Withdrawal request not found");
        requireStatus(withdrawal, "PENDING");
        String old = withdrawal.getStatus();
        releaseWithdrawalReservation(withdrawal);
        withdrawal.setStatus("CANCELLED"); withdrawal.setAdminNote(reason == null || reason.isBlank() ? "Cancelled by owner" : reason.trim());
        withdrawal.setUpdatedAt(Instant.now());
        withdrawal = walletWithdrawalRepository.save(withdrawal);
        recordWithdrawalStatus(withdrawal, old, "CANCELLED", withdrawal.getUser(), "USER_CANCELLED", withdrawal.getAdminNote());
        if (notificationService != null) notificationService.notifyInApp(userId, "Yêu cầu rút tiền đã được hủy",
                "Yêu cầu " + withdrawal.getRequestCode() + " đã được hủy; số tiền đã trở lại số dư khả dụng.",
                NotificationType.WALLET, withdrawal.getId(), "WALLET_WITHDRAWAL", "/wallet");
        return walletMapper.toWithdrawalResponse(withdrawal);
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse confirmReceipt(UUID userId, UUID id) {
        var w = walletWithdrawalRepository.findByIdForUpdate(id)
                .filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        requireStatus(w, "TRANSFERRED_AWAITING_REVIEW");
        Instant now = Instant.now();
        if (!now.isBefore(w.getDisputeDeadline())) throw new ConflictException("Đã hết hạn đối soát");
        w.setStatus("COMPLETED");
        w.setCompletedAt(now);
        w.setUpdatedAt(now);
        recordWithdrawalStatus(w, "TRANSFERRED_AWAITING_REVIEW", "COMPLETED", w.getUser(), "OWNER_CONFIRMED", null);
        return walletMapper.toWithdrawalResponse(w);
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
            notificationService.notifyInApp(w.getUser().getId(), "Bạn đã nhận được tiền chưa?",
                    "Lệnh " + w.getRequestCode() + " còn " + Math.max(1, left.toMinutes()) +
                    " phút để xác nhận đã nhận hoặc báo cáo lỗi.", NotificationType.WALLET, id,
                    "WALLET_WITHDRAWAL", "/wallet/withdrawals/" + id);
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
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public WalletWithdrawalResponse uploadWithdrawalEvidence(UUID staffId, UUID withdrawalId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Ảnh minh chứng đang trống");
        if (file.getSize() >= 5_000_000) throw new BadRequestException("Ảnh minh chứng phải nhỏ hơn 5 MB");
        if (!java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(String.valueOf(file.getContentType())))
            throw new BadRequestException("Chỉ chấp nhận ảnh JPEG, PNG hoặc WEBP");
        if (reportEvidenceStorage == null) throw new IllegalStateException("Private evidence storage is not configured");
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findByIdForUpdate(withdrawalId)
                .orElseThrow(() -> new NotFoundException("Withdrawal request not found"));
        if (!"PROCESSING".equals(withdrawal.getStatus())) throw new ConflictException("Evidence can only be attached to a processing withdrawal");
        if (withdrawal.getAcceptedBy() == null || !withdrawal.getAcceptedBy().getId().equals(staffId))
            throw new ConflictException("Only the assigned staff member may upload transfer evidence");
        try {
            var stored = reportEvidenceStorage.upload(file);
            withdrawal.setEvidenceStorageId(stored.storageId());
            withdrawal.setEvidenceContentType(stored.contentType());
            withdrawal.setEvidenceUploadedBy(userRepository.findById(staffId).orElseThrow(() -> new NotFoundException("Staff not found")));
            withdrawal.setEvidenceUploadedAt(Instant.now());
            withdrawal.setEvidenceUrl("/api/v1/wallets/withdrawals/evidence/" + withdrawal.getId());
            withdrawal.setUpdatedAt(Instant.now());
            return walletMapper.toWithdrawalResponse(walletWithdrawalRepository.save(withdrawal));
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
        return walletMapper.toWithdrawalResponse(withdrawal);
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

    private void releaseWithdrawalReservation(WalletWithdrawal withdrawal) {
        UserWallet wallet = getWalletWithLock(withdrawal.getUser().getId());
        BigDecimal reserved = zeroIfNull(wallet.getReservedBalance());
        if (reserved.compareTo(withdrawal.getAmount()) < 0) throw new ConflictException("Wallet reserved balance is inconsistent");
        wallet.setReservedBalance(reserved.subtract(withdrawal.getAmount()));
        wallet.setBalance(wallet.getBalance().add(withdrawal.getAmount()));
        userWalletRepository.save(wallet);
        createTransaction(wallet, withdrawal.getAmount(), WalletTransactionType.REFUND, withdrawal.getId(), WalletTransactionReferenceType.WITHDRAWAL);
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
        return walletWithdrawalRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(walletMapper::toWithdrawalResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletWithdrawalResponse> getAllWithdrawals(String status, Pageable pageable) {
        if (walletWithdrawalRepository == null) {
            throw new IllegalStateException("WalletWithdrawalRepository is not available");
        }
        if (status != null && !status.isBlank()) {
            return walletWithdrawalRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(), pageable)
                    .map(walletMapper::toWithdrawalResponse);
        }
        return walletWithdrawalRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(walletMapper::toWithdrawalResponse);
    }
}

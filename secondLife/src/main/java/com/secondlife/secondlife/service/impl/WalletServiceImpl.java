package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.WalletTransactionType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.WalletMapper;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.repository.WalletWithdrawalRepository;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {
    private final WalletMapper walletMapper;

    private final UserWalletRepository userWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final WalletWithdrawalRepository walletWithdrawalRepository;

    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository, WalletMapper walletMapper) {
        this(userWalletRepository, walletTransactionRepository, userRepository, null, null, walletMapper);
    }

    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository,
                             NotificationService notificationService, WalletMapper walletMapper) {
        this(userWalletRepository, walletTransactionRepository, userRepository, notificationService, null, walletMapper);
    }

    @Autowired
    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository,
                             @Autowired(required = false) NotificationService notificationService,
                             @Autowired(required = false) WalletWithdrawalRepository walletWithdrawalRepository, WalletMapper walletMapper) {
        this.walletMapper = walletMapper;

        this.userWalletRepository = userWalletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.walletWithdrawalRepository = walletWithdrawalRepository;
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
        createTransaction(wallet, amount.negate(), WalletTransactionType.PAYMENT, orderId);
    }

    @Override
    @Transactional
    public void processEarning(UUID userId, BigDecimal amount, UUID orderId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Earning amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        userWalletRepository.save(wallet);

        createTransaction(wallet, amount, WalletTransactionType.EARNING, orderId);

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
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        userWalletRepository.save(wallet);

        createTransaction(wallet, amount, WalletTransactionType.REFUND, orderId);

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
    public BigDecimal collectReturnShippingFee(UUID sellerId, BigDecimal fee, UUID shipmentId) {
        if (fee == null || fee.signum() < 0 || fee.compareTo(new BigDecimal("1000000000")) > 0) {
            throw new ConflictException("Invalid return shipping fee");
        }
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
            createTransaction(wallet, collected.negate(), WalletTransactionType.RETURN_SHIPPING_FEE, shipmentId);
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

    private void createTransaction(UserWallet wallet, BigDecimal amount, WalletTransactionType type, UUID referenceId) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setReferenceId(referenceId);
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
        if (walletWithdrawalRepository == null) {
            throw new IllegalStateException("WalletWithdrawalRepository is not available");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Số tiền rút phải lớn hơn 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        if (wallet.getBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Số dư khả dụng không đủ để thực hiện rút tiền");
        }

        // Deduct balance from wallet
        wallet.setBalance(wallet.getBalance().subtract(request.amount()));
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
        withdrawal = walletWithdrawalRepository.save(withdrawal);

        createTransaction(wallet, request.amount().negate(), WalletTransactionType.WITHDRAW, withdrawal.getId());

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                userId,
                "Yêu cầu rút tiền đang chờ xử lý",
                "Yêu cầu rút " + request.amount() + " VND về tài khoản " + request.bankCode() + " - " + request.accountNumber() + " đã được tiếp nhận và đang chờ duyệt.",
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

        if (!"PENDING_REVIEW".equalsIgnoreCase(withdrawal.getStatus())) {
            throw new BadRequestException("Yêu cầu rút tiền đã được xử lý trước đó với trạng thái: " + withdrawal.getStatus());
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin user not found"));

        withdrawal.setReviewedBy(admin);
        withdrawal.setReviewedAt(Instant.now());
        withdrawal.setUpdatedAt(Instant.now());
        withdrawal.setAdminNote(request.adminNote());

        if ("APPROVE".equalsIgnoreCase(request.action())) {
            withdrawal.setStatus("APPROVED");
            withdrawal = walletWithdrawalRepository.save(withdrawal);

            if (notificationService != null) {
                notificationService.notifyWithEmail(
                    withdrawal.getUser().getId(),
                    "Yêu cầu rút tiền đã được phê duyệt",
                    "Yêu cầu rút " + withdrawal.getAmount() + " VND của bạn đã được duyệt và chuyển khoản thành công.",
                    NotificationType.WALLET,
                    withdrawal.getId(),
                    "WALLET_WITHDRAWAL",
                    "/wallet"
                );
            }
        } else if ("REJECT".equalsIgnoreCase(request.action())) {
            withdrawal.setStatus("REJECTED");

            // Refund amount back to user's wallet
            UserWallet wallet = getWalletWithLock(withdrawal.getUser().getId());
            wallet.setBalance(wallet.getBalance().add(withdrawal.getAmount()));
            userWalletRepository.save(wallet);

            createTransaction(wallet, withdrawal.getAmount(), WalletTransactionType.REFUND, withdrawal.getId());
            withdrawal = walletWithdrawalRepository.save(withdrawal);

            if (notificationService != null) {
                String note = (request.adminNote() != null && !request.adminNote().isBlank())
                        ? request.adminNote() : "Không có lý do cụ thể";
                notificationService.notifyWithEmail(
                    withdrawal.getUser().getId(),
                    "Yêu cầu rút tiền đã bị từ chối",
                    "Yêu cầu rút " + withdrawal.getAmount() + " VND của bạn đã bị từ chối. Lý do: " + note + ". Số tiền đã được hoàn lại vào ví.",
                    NotificationType.WALLET,
                    withdrawal.getId(),
                    "WALLET_WITHDRAWAL",
                    "/wallet"
                );
            }
        } else {
            throw new BadRequestException("Hành động không hợp lệ: " + request.action());
        }

        return walletMapper.toWithdrawalResponse(withdrawal);
    }

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

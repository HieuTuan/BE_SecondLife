package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.DepositResponseDTO;
import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.entity.DepositRequest;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.WalletTransactionDirection;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Component
public class WalletMapper {
    private final Delegate delegate;

    public WalletMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public WalletResponseDTO toWalletResponse(UserWallet wallet) {
        BigDecimal available = wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance();
        BigDecimal reserved = wallet.getReservedBalance() == null ? BigDecimal.ZERO : wallet.getReservedBalance();
        String status = wallet.getStatus() == null || wallet.getStatus().isBlank() ? "ACTIVE" : wallet.getStatus();
        return new WalletResponseDTO(wallet.getId(), wallet.getUser().getId(), available, wallet.getUpdatedAt(),
                available, reserved, available.add(reserved), status);
    }

    public WalletTransactionResponseDTO toTransactionResponse(WalletTransaction transaction) {
        return delegate.toTransactionResponse(transaction);
    }

    public WalletWithdrawalResponse toWithdrawalResponse(WalletWithdrawal withdrawal) {
        return toWithdrawalResponse(withdrawal, null);
    }

    public WalletWithdrawalResponse toWithdrawalResponse(WalletWithdrawal withdrawal, UserWallet wallet) {
        WalletWithdrawalResponse base = delegate.toWithdrawalResponse(withdrawal);
        if (base == null) {
            return null;
        }
        Instant now = Instant.now();
        Long remainingSeconds = null;
        if ("TRANSFERRED_AWAITING_REVIEW".equals(withdrawal.getStatus()) && withdrawal.getDisputeDeadline() != null) {
            long seconds = Duration.between(now, withdrawal.getDisputeDeadline()).getSeconds();
            remainingSeconds = Math.max(0L, seconds);
        }
        WalletWithdrawalResponse.WalletWithdrawalResponseBuilder builder = base.toBuilder()
                .serverTime(now)
                .remainingDisputeSeconds(remainingSeconds);
        if (wallet != null) {
            builder.availableBalance(wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance())
                    .reservedBalance(wallet.getReservedBalance() == null ? BigDecimal.ZERO : wallet.getReservedBalance())
                    .walletStatus(wallet.getStatus() == null || wallet.getStatus().isBlank() ? "ACTIVE" : wallet.getStatus());
        }
        return builder.build();
    }

    public DepositResponseDTO toDepositResponse(DepositRequest deposit, String bankAccountName, String bankAccountNumber, String bankName) {
        return delegate.toDepositResponse(deposit, bankAccountName, bankAccountNumber, bankName);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "walletId", source = "wallet.id")
        @Mapping(target = "currency", constant = "VND")
        @Mapping(target = "direction", source = "amount", qualifiedByName = "walletDirection")
        @Mapping(target = "description", source = "transaction", qualifiedByName = "walletDescription")
        @Mapping(target = "referenceType", source = "referenceType", defaultValue = "UNKNOWN")
        WalletTransactionResponseDTO toTransactionResponse(WalletTransaction transaction);

        @Named("walletDirection")
        default WalletTransactionDirection walletDirection(BigDecimal amount) {
            if (amount == null || amount.signum() == 0) return WalletTransactionDirection.NEUTRAL;
            return amount.signum() > 0 ? WalletTransactionDirection.CREDIT : WalletTransactionDirection.DEBIT;
        }

        @Named("walletDescription")
        default String walletDescription(WalletTransaction transaction) {
            if (transaction == null || transaction.getType() == null) return "Giao dịch ví";
            WalletTransactionReferenceType referenceType = transaction.getReferenceType() != null
                    ? transaction.getReferenceType() : WalletTransactionReferenceType.UNKNOWN;
            return switch (transaction.getType()) {
                case DEPOSIT -> "Nạp tiền vào ví";
                case WITHDRAW -> "Trừ tiền ví cho yêu cầu rút về ngân hàng";
                case RETURN_SHIPPING_FEE -> "Thu phí vận chuyển hoàn hàng từ ví người bán";
                case SHIPPING_REIMBURSE -> "Hoàn ứng cước vận chuyển kiểm định (Sandbox)";
                case PAYMENT -> switch (referenceType) {
                    case ORDER -> "Thanh toán đơn hàng từ ví";
                    case INSPECTION_ORDER -> "Thanh toán cho đơn kiểm định từ ví";
                    case CREDIT_PURCHASE -> "Mua quyền sử dụng dịch vụ bằng số dư ví";
                    case TOPUP_PACKAGE -> "Mua gói quyền sử dụng dịch vụ bằng số dư ví";
                    default -> "Thanh toán từ ví";
                };
                case EARNING -> switch (referenceType) {
                    case INSPECTION_ORDER -> "Nhận tiền từ giao dịch kiểm định";
                    case ORDER -> "Nhận tiền bán hàng vào ví";
                    default -> "Nhận tiền vào ví";
                };
                case REFUND -> switch (referenceType) {
                    case WITHDRAWAL -> "Giải phóng số dư phong tỏa do yêu cầu rút tiền bị hủy/từ chối";
                    case INSPECTION_ORDER -> "Hoàn tiền cho giao dịch kiểm định";
                    case ORDER -> "Hoàn tiền đơn hàng vào ví";
                    default -> "Hoàn tiền vào ví";
                };
            };
        }

        @Mapping(target = "userId", source = "user.id")
        @Mapping(target = "userEmail", source = "user.email")
        @Mapping(target = "reviewedBy", source = "reviewedBy.id")
        @Mapping(target = "requestCode", source = "requestCode")
        @Mapping(target = "bankName", source = "bankName")
        @Mapping(target = "acceptedBy", source = "acceptedBy.id")
        @Mapping(target = "transferredBy", source = "transferredBy.id")
        @Mapping(target = "availableBalance", ignore = true)
        @Mapping(target = "reservedBalance", ignore = true)
        @Mapping(target = "serverTime", ignore = true)
        @Mapping(target = "remainingDisputeSeconds", ignore = true)
        @Mapping(target = "walletStatus", ignore = true)
        @Mapping(target = "disputeEvidenceUrls", source = "disputeEvidenceUrls", qualifiedByName = "splitEvidenceUrls")
        @Mapping(target = "exceptionEvidenceUrls", source = "exceptionEvidenceUrls", qualifiedByName = "splitEvidenceUrls")
        @Mapping(target = "ownerRole", source = "user", qualifiedByName = "withdrawalOwnerRole")
        WalletWithdrawalResponse toWithdrawalResponse(WalletWithdrawal withdrawal);

        @Named("splitEvidenceUrls")
        default List<String> splitEvidenceUrls(String raw) {
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return Arrays.stream(raw.split("\\r?\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        @Named("withdrawalOwnerRole")
        default String withdrawalOwnerRole(User user) {
            if (user == null) return "UNKNOWN";
            if (user.hasRole("INSPECTION_CENTER")) return "INSPECTION_CENTER";
            if (user.hasRole("INSPECTOR")) return "INSPECTOR";
            if (user.hasRole("SELLER")) return "SELLER";
            if (user.hasRole("BUYER")) return "BUYER";
            return "UNKNOWN";
        }

        @Mapping(target = "id", source = "deposit.id")
        @Mapping(target = "amount", source = "deposit.amount")
        @Mapping(target = "code", source = "deposit.code")
        @Mapping(target = "status", source = "deposit.status")
        @Mapping(target = "createdAt", source = "deposit.createdAt")
        @Mapping(target = "bankAccountName", source = "bankAccountName")
        @Mapping(target = "bankAccountNumber", source = "bankAccountNumber")
        @Mapping(target = "bankName", source = "bankName")
        DepositResponseDTO toDepositResponse(DepositRequest deposit, String bankAccountName, String bankAccountNumber, String bankName);
    }
}

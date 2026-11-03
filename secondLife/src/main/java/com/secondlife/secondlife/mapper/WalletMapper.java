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
import com.secondlife.secondlife.enums.WalletTransactionDirection;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class WalletMapper {
    private final Delegate delegate;

    public WalletMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public WalletResponseDTO toWalletResponse(UserWallet wallet) {
        return delegate.toWalletResponse(wallet);
    }

    public WalletTransactionResponseDTO toTransactionResponse(WalletTransaction transaction) {
        return delegate.toTransactionResponse(transaction);
    }

    public WalletWithdrawalResponse toWithdrawalResponse(WalletWithdrawal withdrawal) {
        return delegate.toWithdrawalResponse(withdrawal);
    }

    public DepositResponseDTO toDepositResponse(DepositRequest deposit, String bankAccountName, String bankAccountNumber, String bankName) {
        return delegate.toDepositResponse(deposit, bankAccountName, bankAccountNumber, bankName);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "userId", source = "user.id")
        WalletResponseDTO toWalletResponse(UserWallet wallet);

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
                    case WITHDRAWAL -> "Hoàn tiền do yêu cầu rút tiền bị từ chối";
                    case INSPECTION_ORDER -> "Hoàn tiền cho giao dịch kiểm định";
                    case ORDER -> "Hoàn tiền đơn hàng vào ví";
                    default -> "Hoàn tiền vào ví";
                };
            };
        }

        @Mapping(target = "userId", source = "user.id")
        @Mapping(target = "userEmail", source = "user.email")
        @Mapping(target = "reviewedBy", source = "reviewedBy.id")
        WalletWithdrawalResponse toWithdrawalResponse(WalletWithdrawal withdrawal);

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

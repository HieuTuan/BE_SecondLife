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
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

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
        WalletTransactionResponseDTO toTransactionResponse(WalletTransaction transaction);

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

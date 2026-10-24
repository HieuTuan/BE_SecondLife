package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.SystemWalletResponse;
import com.secondlife.secondlife.dto.response.SystemWalletTransactionResponse;
import com.secondlife.secondlife.entity.SystemWallet;
import com.secondlife.secondlife.entity.SystemWalletTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class SystemWalletMapper {
    private final Delegate delegate;

    public SystemWalletMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public SystemWalletTransactionResponse toTransactionResponse(SystemWalletTransaction transaction) {
        return delegate.toTransactionResponse(transaction);
    }

    public SystemWalletResponse toResponse(SystemWallet wallet) {
        return delegate.toResponse(wallet);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        SystemWalletTransactionResponse toTransactionResponse(SystemWalletTransaction transaction);

        @Mapping(target = "totalBalance", expression = "java(wallet.getHeldBalance().add(wallet.getShippingBalance()).add(wallet.getRevenueBalance()))")
        SystemWalletResponse toResponse(SystemWallet wallet);
    }
}

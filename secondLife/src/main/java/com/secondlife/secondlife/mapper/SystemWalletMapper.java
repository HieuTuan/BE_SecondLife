package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.SystemWalletResponse;
import com.secondlife.secondlife.dto.response.SystemWalletTransactionResponse;
import com.secondlife.secondlife.entity.SystemWallet;
import com.secondlife.secondlife.entity.SystemWalletStatementEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SystemWalletMapper {
    private final Delegate delegate;

    public SystemWalletMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public SystemWalletTransactionResponse toTransactionResponse(SystemWalletStatementEntry entry, BigDecimal userShippingFee) {
        return delegate.toTransactionResponse(entry, userShippingFee);
    }

    public SystemWalletResponse toResponse(SystemWallet wallet, BigDecimal inspectionHeldBalance, BigDecimal totalHubPaid,
                                           BigDecimal packageRevenueBalance) {
        return delegate.toResponse(wallet, inspectionHeldBalance, totalHubPaid, packageRevenueBalance);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "userShippingFee", source = "userShippingFee")
        SystemWalletTransactionResponse toTransactionResponse(SystemWalletStatementEntry entry, BigDecimal userShippingFee);

        @Mapping(target = "totalBalance", expression = "java(wallet.getHeldBalance().add(wallet.getShippingBalance()).add(wallet.getRevenueBalance()))")
        @Mapping(target = "heldBalance", expression = "java(wallet.getHeldBalance().subtract(inspectionHeldBalance))")
        @Mapping(target = "totalHubPaid", source = "totalHubPaid")
        @Mapping(target = "inspectionHeldBalance", source = "inspectionHeldBalance")
        @Mapping(target = "packageRevenueBalance", source = "packageRevenueBalance")
        SystemWalletResponse toResponse(SystemWallet wallet, BigDecimal inspectionHeldBalance, BigDecimal totalHubPaid,
                                        BigDecimal packageRevenueBalance);
    }
}

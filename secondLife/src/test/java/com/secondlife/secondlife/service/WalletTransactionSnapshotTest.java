package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.enums.WalletTransactionType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import com.secondlife.secondlife.mapper.WalletMapper;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletTransactionSnapshotTest {
    private final UserWalletRepository wallets = mock(UserWalletRepository.class);
    private final WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
    private final WalletMapper mapper = MapperTestSupport.mapper(WalletMapper.class);
    private final UUID userId = UUID.randomUUID();
    private final UserWallet wallet = new UserWallet();
    private WalletServiceImpl service;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(userId);
        wallet.setId(UUID.randomUUID());
        wallet.setUser(user);
        wallet.setBalance(new BigDecimal("1000.00"));
        when(wallets.findByUserIdWithLock(userId)).thenReturn(Optional.of(wallet));
        service = new WalletServiceImpl(wallets, transactions, mock(UserRepository.class), mapper);
    }

    @ParameterizedTest
    @EnumSource(value = WalletTransactionType.class,
            names = {"DEPOSIT", "PAYMENT", "EARNING", "REFUND", "RETURN_SHIPPING_FEE"})
    void snapshotsFollowTheActualSignedWalletMovement(WalletTransactionType type) {
        BigDecimal amount = new BigDecimal("100.00");
        UUID referenceId = UUID.randomUUID();
        switch (type) {
            case DEPOSIT -> service.deposit(userId, amount, referenceId);
            case PAYMENT -> service.processPayment(userId, amount, referenceId);
            case EARNING -> service.processEarning(userId, amount, referenceId);
            case REFUND -> service.processRefund(userId, amount, referenceId);
            case RETURN_SHIPPING_FEE -> service.collectReturnShippingFee(userId, amount, referenceId);
            default -> throw new AssertionError(type);
        }

        ArgumentCaptor<WalletTransaction> recorded = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactions).save(recorded.capture());
        var body = JsonMapper.builder().build().valueToTree(mapper.toTransactionResponse(recorded.getValue()));
        assertTrue(body.hasNonNull("balanceBefore"), "A new transaction must have its previous balance");
        assertTrue(body.hasNonNull("balanceAfter"), "A new transaction must have its resulting balance");
        assertEquals(0, new BigDecimal("1000.00").compareTo(body.path("balanceBefore").decimalValue()));
        assertEquals(0, wallet.getBalance().compareTo(body.path("balanceAfter").decimalValue()));
        assertEquals(0, wallet.getBalance().subtract(new BigDecimal("1000.00"))
                .compareTo(recorded.getValue().getAmount()));
        assertEquals("VND", body.path("currency").asText());
        assertTrue(body.path("description").asText().length() > 5);
        assertEquals(recorded.getValue().getAmount().signum() > 0 ? "CREDIT" : "DEBIT",
                body.path("direction").asText());
    }

    @ParameterizedTest
    @EnumSource(value = WalletTransactionType.class, names = {"DEPOSIT", "PAYMENT", "EARNING", "REFUND"})
    void subCentAmountsAreRejectedBeforeTheyCanBeRoundedDifferently(WalletTransactionType type) {
        BigDecimal amount = new BigDecimal("100.005");
        UUID referenceId = UUID.randomUUID();
        assertThrows(BadRequestException.class, () -> {
            switch (type) {
                case DEPOSIT -> service.deposit(userId, amount, referenceId);
                case PAYMENT -> service.processPayment(userId, amount, referenceId);
                case EARNING -> service.processEarning(userId, amount, referenceId);
                case REFUND -> service.processRefund(userId, amount, referenceId);
                default -> throw new AssertionError(type);
            }
        });
        assertEquals(new BigDecimal("1000.00"), wallet.getBalance());
    }
}

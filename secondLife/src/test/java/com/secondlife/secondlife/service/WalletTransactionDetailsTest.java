package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.enums.WalletTransactionType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import com.secondlife.secondlife.mapper.WalletMapper;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.service.impl.WalletQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalletTransactionDetailsTest {
    private final WalletTransactionRepository transactions = mock(WalletTransactionRepository.class);
    private final WalletService wallets = mock(WalletService.class);
    private final WalletMapper mapper = MapperTestSupport.mapper(WalletMapper.class);
    private final WalletQueryServiceImpl service = new WalletQueryServiceImpl(wallets, mapper, transactions);
    private final UUID userId = UUID.randomUUID();

    @Test
    void legacyTransactionsRetainNullSnapshotsAndTheOriginalFields() {
        WalletTransaction transaction = transaction(WalletTransactionType.PAYMENT, new BigDecimal("-10.00"));
        var response = mapper.toTransactionResponse(transaction);
        assertEquals(transaction.getId(), response.id());
        assertEquals(transaction.getWallet().getId(), response.walletId());
        assertEquals(transaction.getReferenceId(), response.referenceId());
        assertEquals(transaction.getCreatedAt(), response.createdAt());
        assertEquals(transaction.getAmount(), response.amount());
        assertEquals(WalletTransactionReferenceType.UNKNOWN, response.referenceType());
        assertEquals("Thanh toán từ ví", response.description());
        assertNull(response.balanceBefore());
        assertNull(response.balanceAfter());
    }

    @ParameterizedTest
    @EnumSource(WalletTransactionType.class)
    void everyTransactionTypeHasADescriptionAndSignedDirection(WalletTransactionType type) {
        WalletTransaction transaction = transaction(type, BigDecimal.TEN.negate());
        var response = mapper.toTransactionResponse(transaction);
        assertEquals("DEBIT", response.direction().name());
        assertEquals("VND", response.currency());
        assertFalse(response.description().isBlank());
        assertEquals("CREDIT", mapper.toTransactionResponse(transaction(type, BigDecimal.TEN)).direction().name());
        assertEquals("NEUTRAL", mapper.toTransactionResponse(transaction(type, BigDecimal.ZERO)).direction().name());
    }

    @Test
    void rejectedWithdrawalAndReturnShippingHaveAccurateDescriptions() {
        WalletTransaction refund = transaction(WalletTransactionType.REFUND, BigDecimal.TEN);
        refund.setReferenceType(WalletTransactionReferenceType.WITHDRAWAL);
        assertEquals("Hoàn tiền do yêu cầu rút tiền bị từ chối", mapper.toTransactionResponse(refund).description());
        WalletTransaction fee = transaction(WalletTransactionType.RETURN_SHIPPING_FEE, BigDecimal.TEN.negate());
        assertEquals("Thu phí vận chuyển hoàn hàng từ ví người bán", mapper.toTransactionResponse(fee).description());
    }

    @Test
    void detailLookupIncludesTheAuthenticatedOwnerInTheDatabasePredicate() {
        WalletTransaction transaction = transaction(WalletTransactionType.DEPOSIT, BigDecimal.TEN);
        when(transactions.findByIdAndWalletUserId(transaction.getId(), userId)).thenReturn(Optional.of(transaction));
        assertEquals(transaction.getId(), service.getTransaction(userId, transaction.getId()).id());
        verify(transactions).findByIdAndWalletUserId(transaction.getId(), userId);
        verifyNoInteractions(wallets);
    }

    @Test
    void unknownOrForeignTransactionDoesNotFallBackToAnUnscopedLookup() {
        UUID transactionId = UUID.randomUUID();
        when(transactions.findByIdAndWalletUserId(transactionId, userId)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getTransaction(userId, transactionId));
        verify(transactions).findByIdAndWalletUserId(transactionId, userId);
        verifyNoInteractions(wallets);
    }

    @Test
    void listDefaultsToStableNewestFirstAndDoesNotCreateAWallet() {
        when(transactions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction(WalletTransactionType.DEPOSIT, BigDecimal.TEN))));
        assertEquals(1, service.getTransactions(userId, PageRequest.of(2, 10)).getNumberOfElements());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).findAll(any(Specification.class), page.capture());
        assertEquals(2, page.getValue().getPageNumber());
        assertEquals(10, page.getValue().getPageSize());
        assertEquals(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), page.getValue().getSort());
        verifyNoInteractions(wallets);
    }

    @Test
    void invalidTimeRangeAndUnknownSortFailBeforeQuerying() {
        Instant instant = Instant.parse("2026-10-25T00:00:00Z");
        assertThrows(BadRequestException.class,
                () -> service.getTransactions(userId, null, instant, instant, PageRequest.of(0, 10)));
        assertThrows(BadRequestException.class,
                () -> service.getTransactions(userId, PageRequest.of(0, 10, Sort.by("string"))));
        assertThrows(BadRequestException.class,
                () -> service.getTransactions(userId, PageRequest.of(0, 10, Sort.by(Sort.Order.asc("amount").ignoreCase()))));
        verifyNoInteractions(transactions, wallets);
    }

    @Test
    void clientSortingKeepsItsPriorityAndGetsAUniqueTieBreaker() {
        when(transactions.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        var requested = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "amount"));
        service.getTransactions(userId, null, null, null, requested);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(transactions).findAll(any(Specification.class), page.capture());
        assertEquals(Sort.by(Sort.Order.asc("amount"), Sort.Order.desc("id")), page.getValue().getSort());
    }

    private WalletTransaction transaction(WalletTransactionType type, BigDecimal amount) {
        UserWallet wallet = new UserWallet();
        wallet.setId(UUID.randomUUID());
        WalletTransaction transaction = new WalletTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setWallet(wallet);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setReferenceId(UUID.randomUUID());
        transaction.setCreatedAt(Instant.parse("2026-10-25T00:00:00Z"));
        return transaction;
    }
}

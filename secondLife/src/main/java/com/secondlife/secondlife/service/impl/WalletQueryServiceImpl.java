package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.WalletResponseDTO;
import com.secondlife.secondlife.dto.response.WalletTransactionResponseDTO;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.enums.WalletTransactionType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.WalletMapper;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.service.WalletQueryService;
import com.secondlife.secondlife.service.WalletService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletQueryServiceImpl implements WalletQueryService {

    private final WalletService walletService;
    private final WalletMapper walletMapper;
    private final WalletTransactionRepository transactions;

    @Override
    @Transactional
    public WalletResponseDTO getWallet(UUID userId) {
        return walletMapper.toWalletResponse(walletService.getWalletByUserId(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponseDTO> getTransactions(UUID userId, Pageable pageable) {
        return getTransactions(userId, null, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WalletTransactionResponseDTO> getTransactions(UUID userId, WalletTransactionType type,
                                                             Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("from must be earlier than to");
        }
        Specification<WalletTransaction> filter = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("wallet").get("user").get("id"), userId));
            if (type != null) predicates.add(builder.equal(root.get("type"), type));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null) predicates.add(builder.lessThan(root.get("createdAt"), to));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return transactions.findAll(filter, sorted(pageable)).map(walletMapper::toTransactionResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletTransactionResponseDTO getTransaction(UUID userId, UUID transactionId) {
        WalletTransaction transaction = transactions.findByIdAndWalletUserId(transactionId, userId)
                .orElseThrow(() -> new NotFoundException("Wallet transaction not found"));
        return walletMapper.toTransactionResponse(transaction);
    }

    private Pageable sorted(Pageable pageable) {
        Set<String> supported = Set.of("id", "createdAt", "amount", "type", "referenceId");
        for (Sort.Order order : pageable.getSort()) {
            if (!supported.contains(order.getProperty()) || order.isIgnoreCase()) {
                throw new BadRequestException("Unsupported wallet transaction sort field: " + order.getProperty());
            }
        }
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        return pageable.isPaged() ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                : Pageable.unpaged(sort);
    }
}

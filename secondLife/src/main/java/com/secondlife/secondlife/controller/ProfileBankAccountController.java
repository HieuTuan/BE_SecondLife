package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/bank-accounts")
@PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
@RequiredArgsConstructor
public class ProfileBankAccountController {
    private final CurrentUserProvider currentUser;
    private final JdbcClient jdbc;

    public record Details(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,20}") String bankCode,
            @NotBlank @Size(max = 120) String bankName,
            @NotBlank @Pattern(regexp = "[0-9]{6,30}") String accountNumber,
            @NotBlank @Size(min = 2, max = 150) String accountHolderName
    ) {}

    public record Account(
            UUID id,
            String bankCode,
            String bankName,
            String accountNumber,
            String accountHolderName,
            boolean isPrimary,
            Instant updatedAt
    ) {}

    public record AccountHistoryItem(
            UUID id,
            UUID bankAccountId,
            String action,
            String previousBankCode,
            String previousBankName,
            String previousAccountNumber,
            String previousAccountHolderName,
            String bankCode,
            String bankName,
            String accountNumber,
            String accountHolderName,
            Instant createdAt
    ) {}

    @GetMapping
    @Transactional
    public List<Account> list(@AuthenticationPrincipal CustomUserDetails principal) {
        UUID userId = currentUser.resolveUserId(principal);
        List<Account> accounts = jdbc.sql("""
                select id, bank_code, bank_name, account_number, account_holder_name, is_primary, updated_at
                from user_bank_accounts
                where user_id = ?
                order by is_primary desc, updated_at desc, id desc
                """)
                .param(userId)
                .query(Account.class)
                .list();
        if (!accounts.isEmpty()) {
            return accounts;
        }

        // Backfill from user's most recent withdrawal if they have withdrawn before saving to user_bank_accounts
        Optional<Details> latestWithdrawalBank = jdbc.sql("""
                select bank_code,
                       coalesce(nullif(trim(bank_name), ''), bank_code) as bank_name,
                       account_number,
                       account_holder_name
                from wallet_withdrawals
                where user_id = ?
                  and account_number is not null and trim(account_number) <> ''
                  and account_holder_name is not null and trim(account_holder_name) <> ''
                order by created_at desc
                limit 1
                """)
                .param(userId)
                .query(Details.class)
                .optional();

        if (latestWithdrawalBank.isEmpty()) {
            return accounts;
        }

        Details d = latestWithdrawalBank.get();
        try {
            UUID savedId = jdbc.sql("""
                    insert into user_bank_accounts(user_id, bank_code, bank_name, account_number, account_holder_name, is_primary, updated_at)
                    values (?, ?, ?, ?, ?, true, now())
                    on conflict(user_id, bank_code, bank_name, account_number)
                    do update set account_holder_name = excluded.account_holder_name,
                                  is_primary = true,
                                  updated_at = now()
                    returning id
                    """)
                    .params(
                            userId,
                            d.bankCode().trim(),
                            d.bankName().trim(),
                            d.accountNumber().trim(),
                            d.accountHolderName().trim()
                    )
                    .query(UUID.class)
                    .single();
            recordHistory(userId, savedId, "MIGRATION_BACKFILL", null, d);
            return jdbc.sql("""
                    select id, bank_code, bank_name, account_number, account_holder_name, is_primary, updated_at
                    from user_bank_accounts
                    where user_id = ?
                    order by is_primary desc, updated_at desc, id desc
                    """)
                    .param(userId)
                    .query(Account.class)
                    .list();
        } catch (Exception ignored) {
            return accounts;
        }
    }

    @GetMapping("/history")
    public List<AccountHistoryItem> history(@AuthenticationPrincipal CustomUserDetails principal) {
        UUID userId = currentUser.resolveUserId(principal);
        return jdbc.sql("""
                select id, bank_account_id, action,
                       previous_bank_code, previous_bank_name, previous_account_number, previous_account_holder_name,
                       bank_code, bank_name, account_number, account_holder_name, created_at
                from user_bank_account_history
                where user_id = ?
                order by created_at desc
                limit 50
                """)
                .param(userId)
                .query(AccountHistoryItem.class)
                .list();
    }

    @PostMapping
    @Transactional
    public UUID save(@AuthenticationPrincipal CustomUserDetails principal, @Valid @RequestBody Details d) {
        UUID userId = currentUser.resolveUserId(principal);
        Optional<Account> previousPrimary = findPrimaryOrLatest(userId);

        jdbc.sql("update user_bank_accounts set is_primary = false where user_id = ?")
                .param(userId)
                .update();

        UUID savedId = jdbc.sql("""
                insert into user_bank_accounts(user_id, bank_code, bank_name, account_number, account_holder_name, is_primary, updated_at)
                values (?, ?, ?, ?, ?, true, now())
                on conflict(user_id, bank_code, bank_name, account_number)
                do update set account_holder_name = excluded.account_holder_name,
                              is_primary = true,
                              updated_at = now()
                returning id
                """)
                .params(
                        userId,
                        d.bankCode().trim(),
                        d.bankName().trim(),
                        d.accountNumber().trim(),
                        d.accountHolderName().trim()
                )
                .query(UUID.class)
                .single();

        boolean changedFromPrevious = previousPrimary.isEmpty()
                || !previousPrimary.get().bankCode().equalsIgnoreCase(d.bankCode().trim())
                || !previousPrimary.get().accountNumber().equals(d.accountNumber().trim())
                || !previousPrimary.get().accountHolderName().equalsIgnoreCase(d.accountHolderName().trim());

        if (changedFromPrevious) {
            recordHistory(
                    userId,
                    savedId,
                    previousPrimary.isPresent() ? "REPLACED" : "CREATED",
                    previousPrimary.orElse(null),
                    d
            );
        }
        return savedId;
    }

    @PutMapping("/{id}")
    @Transactional
    public void edit(@AuthenticationPrincipal CustomUserDetails principal,
                     @PathVariable UUID id,
                     @Valid @RequestBody Details d) {
        UUID userId = currentUser.resolveUserId(principal);
        Account existing = jdbc.sql("""
                select id, bank_code, bank_name, account_number, account_holder_name, is_primary, updated_at
                from user_bank_accounts
                where id = ? and user_id = ?
                """)
                .params(id, userId)
                .query(Account.class)
                .optional()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản ngân hàng"));

        jdbc.sql("update user_bank_accounts set is_primary = false where user_id = ?")
                .param(userId)
                .update();

        jdbc.sql("""
                update user_bank_accounts
                set bank_code = ?, bank_name = ?, account_number = ?, account_holder_name = ?,
                    is_primary = true, updated_at = now()
                where id = ? and user_id = ?
                """)
                .params(
                        d.bankCode().trim(),
                        d.bankName().trim(),
                        d.accountNumber().trim(),
                        d.accountHolderName().trim(),
                        id,
                        userId
                )
                .update();

        recordHistory(userId, id, "UPDATED", existing, d);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        UUID userId = currentUser.resolveUserId(principal);
        Account existing = jdbc.sql("""
                select id, bank_code, bank_name, account_number, account_holder_name, is_primary, updated_at
                from user_bank_accounts
                where id = ? and user_id = ?
                """)
                .params(id, userId)
                .query(Account.class)
                .optional()
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản ngân hàng"));

        jdbc.sql("delete from user_bank_accounts where id = ? and user_id = ?")
                .params(id, userId)
                .update();

        if (existing.isPrimary()) {
            jdbc.sql("""
                    update user_bank_accounts
                    set is_primary = true
                    where id = (
                        select id from user_bank_accounts
                        where user_id = ?
                        order by updated_at desc, id desc
                        limit 1
                    )
                    """)
                    .param(userId)
                    .update();
        }

        jdbc.sql("""
                insert into user_bank_account_history(
                    user_id, bank_account_id, action,
                    previous_bank_code, previous_bank_name, previous_account_number, previous_account_holder_name
                ) values (?, ?, 'DELETED', ?, ?, ?, ?)
                """)
                .params(
                        userId,
                        id,
                        existing.bankCode(),
                        existing.bankName(),
                        existing.accountNumber(),
                        existing.accountHolderName()
                )
                .update();
    }

    private Optional<Account> findPrimaryOrLatest(UUID userId) {
        return jdbc.sql("""
                select id, bank_code, bank_name, account_number, account_holder_name, is_primary, updated_at
                from user_bank_accounts
                where user_id = ?
                order by is_primary desc, updated_at desc, id desc
                limit 1
                """)
                .param(userId)
                .query(Account.class)
                .optional();
    }

    private void recordHistory(UUID userId, UUID accountId, String action, Account previous, Details next) {
        jdbc.sql("""
                insert into user_bank_account_history(
                    user_id, bank_account_id, action,
                    previous_bank_code, previous_bank_name, previous_account_number, previous_account_holder_name,
                    bank_code, bank_name, account_number, account_holder_name
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(
                        userId,
                        accountId,
                        action,
                        previous != null ? previous.bankCode() : null,
                        previous != null ? previous.bankName() : null,
                        previous != null ? previous.accountNumber() : null,
                        previous != null ? previous.accountHolderName() : null,
                        next.bankCode().trim(),
                        next.bankName().trim(),
                        next.accountNumber().trim(),
                        next.accountHolderName().trim()
                )
                .update();
    }
}

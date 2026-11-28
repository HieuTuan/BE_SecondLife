package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/bank-accounts")
@PreAuthorize("isAuthenticated() and (!hasRole('INSPECTOR') or hasRole('INSPECTION_CENTER'))")
@RequiredArgsConstructor
public class ProfileBankAccountController {
    private final CurrentUserProvider currentUser;
    private final JdbcClient jdbc;

    public record Details(@NotBlank @Pattern(regexp="[A-Za-z0-9]{2,20}") String bankCode,
                          @NotBlank @Size(max=120) String bankName,
                          @NotBlank @Pattern(regexp="[0-9]{6,30}") String accountNumber,
                          @NotBlank @Size(min=2,max=150) String accountHolderName) {}
    public record Account(UUID id, String bankCode, String bankName, String accountNumber, String accountHolderName) {}

    @GetMapping
    public List<Account> list(@AuthenticationPrincipal CustomUserDetails principal) {
        return jdbc.sql("select id, bank_code, bank_name, account_number, account_holder_name from user_bank_accounts where user_id=? order by bank_name,id")
                .param(currentUser.resolveUserId(principal)).query(Account.class).list();
    }

    @PostMapping
    public UUID save(@AuthenticationPrincipal CustomUserDetails principal, @Valid @RequestBody Details d) {
        return jdbc.sql("""
                insert into user_bank_accounts(user_id,bank_code,bank_name,account_number,account_holder_name)
                values (?,?,?,?,?) on conflict(user_id,bank_code,bank_name,account_number)
                do update set account_holder_name=excluded.account_holder_name returning id
                """).params(currentUser.resolveUserId(principal),d.bankCode(),d.bankName().trim(),d.accountNumber(),d.accountHolderName().trim())
                .query(UUID.class).single();
    }

    @PutMapping("/{id}")
    public void edit(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id, @Valid @RequestBody Details d) {
        int count = jdbc.sql("update user_bank_accounts set bank_code=?,bank_name=?,account_number=?, account_holder_name=? where id=? and user_id=?")
                .params(d.bankCode(),d.bankName().trim(),d.accountNumber(),d.accountHolderName().trim(),id,currentUser.resolveUserId(principal)).update();
        if (count == 0) throw new NotFoundException("Không tìm thấy tài khoản ngân hàng");
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        int count = jdbc.sql("delete from user_bank_accounts where id=? and user_id=?").params(id,currentUser.resolveUserId(principal)).update();
        if (count == 0) throw new NotFoundException("Không tìm thấy tài khoản ngân hàng");
    }
}

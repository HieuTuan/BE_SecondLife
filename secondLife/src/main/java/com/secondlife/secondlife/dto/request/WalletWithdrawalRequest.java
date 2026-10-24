package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WalletWithdrawalRequest(
    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "50000", message = "Số tiền rút tối thiểu là 50,000 VND")
    BigDecimal amount,
    @NotBlank(message = "Mã ngân hàng không được để trống (vd: VCB, MB, TCB)")
    String bankCode,
    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    String accountNumber,
    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    String accountHolderName
) {
}

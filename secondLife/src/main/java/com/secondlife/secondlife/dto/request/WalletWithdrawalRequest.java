package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record WalletWithdrawalRequest(
    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "10000", message = "Số tiền rút tối thiểu là 10.000 VND")
    BigDecimal amount,
    @NotBlank(message = "Mã ngân hàng không được để trống (vd: VCB, MB, TCB)")
    @Pattern(regexp = "^[A-Za-z0-9]{2,20}$", message = "Mã ngân hàng không hợp lệ")
    String bankCode,
    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    @Pattern(regexp = "^[0-9]{6,30}$", message = "Số tài khoản phải có từ 6 đến 30 chữ số")
    String accountNumber,
    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    @Size(min = 2, max = 150, message = "Tên chủ tài khoản phải có từ 2 đến 150 ký tự")
    String accountHolderName,
    @Size(max = 120, message = "Tên ngân hàng quá dài")
    String bankName
) {
    public WalletWithdrawalRequest(BigDecimal amount, String bankCode, String accountNumber, String accountHolderName) {
        this(amount, bankCode, accountNumber, accountHolderName, null);
    }
}

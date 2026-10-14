package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletWithdrawalRequest {

    @NotNull(message = "Số tiền rút không được để trống")
    @DecimalMin(value = "50000", message = "Số tiền rút tối thiểu là 50,000 VND")
    private BigDecimal amount;

    @NotBlank(message = "Mã ngân hàng không được để trống (vd: VCB, MB, TCB)")
    private String bankCode;

    @NotBlank(message = "Số tài khoản ngân hàng không được để trống")
    private String accountNumber;

    @NotBlank(message = "Tên chủ tài khoản không được để trống")
    private String accountHolderName;
}

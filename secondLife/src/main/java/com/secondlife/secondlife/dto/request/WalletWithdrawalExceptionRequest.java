package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WalletWithdrawalExceptionRequest(
        @NotBlank(message = "Vui lòng nhập lý do gửi yêu cầu hỗ trợ ngoại lệ")
        @Size(max = 2000, message = "Lý do không được vượt quá 2000 ký tự")
        String reason,
        List<String> evidenceUrls
) {
    public WalletWithdrawalExceptionRequest(String reason) {
        this(reason, List.of());
    }
}

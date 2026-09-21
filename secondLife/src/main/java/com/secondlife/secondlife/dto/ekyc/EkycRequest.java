package com.secondlife.secondlife.dto.ekyc;

import com.secondlife.secondlife.enums.VerificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EkycRequest(
        @NotNull(message = "Loại giấy tờ không được để trống")
        VerificationType documentType,

        @NotBlank(message = "Số giấy tờ không được để trống")
        String documentNumber,

        @NotBlank(message = "URL ảnh mặt trước không được để trống")
        String documentFrontUrl,

        @NotBlank(message = "URL ảnh mặt sau không được để trống")
        String documentBackUrl,

        String selfieUrl
) {}

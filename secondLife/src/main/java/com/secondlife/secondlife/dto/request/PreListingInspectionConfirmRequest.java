package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PreListingInspectionConfirmRequest(
        @NotNull UUID quoteId,
        @AssertTrue(message = "Cần đồng ý điều khoản kiểm định và mở niêm phong") boolean sellerConsentZin) {
}

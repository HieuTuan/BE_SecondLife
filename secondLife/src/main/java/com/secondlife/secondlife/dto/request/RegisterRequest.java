package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    String email,

    @NotBlank(message = "Password is required")
    @StrongPassword
    String password,

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    String fullName,

    @Pattern(regexp = "^0[35789][0-9]{8}$", message = "Số điện thoại phải gồm đúng 10 chữ số và bắt đầu bằng 03, 05, 07, 08 hoặc 09")
    @io.swagger.v3.oas.annotations.media.Schema(description = "Số điện thoại Việt Nam gồm 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09", example = "0912345678")
    String phone,

    @io.swagger.v3.oas.annotations.media.Schema(description="Optional gender: MALE (Nam), FEMALE (Nữ), OTHER (Khác)", example="MALE", allowableValues={"MALE","FEMALE","OTHER"})
    com.secondlife.secondlife.enums.Gender gender
) {
    public RegisterRequest(String email,String password,String fullName,String phone) {
        this(email,password,fullName,phone,null);
    }
}

package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.*;

public record CategoryRequest(@NotBlank @Size(max = 255) String name, @Size(max = 2000) String description) {}

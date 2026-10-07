package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record ItemRequest(@NotNull UUID categoryId, @NotBlank @Size(max = 255) String name) {}

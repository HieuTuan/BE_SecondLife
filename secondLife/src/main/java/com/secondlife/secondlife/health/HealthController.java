package com.secondlife.secondlife.health;

import com.secondlife.secondlife.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    @Operation(summary = "Application health", description = "Reports that the application can serve HTTP requests")
    @SecurityRequirements
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success("Application is running", new HealthResponse("UP"));
    }

    public record HealthResponse(String status) {
    }
}

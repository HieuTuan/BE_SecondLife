package com.secondlife.secondlife.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void carrierRateLimitReturns429InsteadOfBadGateway() throws Exception {
        mockMvc.perform(get("/test/rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Lalamove request failed (HTTP 429, ERR_RATE_LIMIT_EXCEEDED)"));
    }
    @Test
    void notFoundReturnsStandardErrorEnvelope() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing item"))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unexpectedErrorDoesNotExposeInternalMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("An unexpected internal error occurred. Please try again later."));
    }

    @Test
    void queryTimeoutReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/test/timeout"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Database operation timed out or lock conflict occurred. Please retry."))
                .andExpect(jsonPath("$.path").value("/test/timeout"));
    }

    @RestController
    @RequestMapping("/test")
    static class FailingController {
        @GetMapping("/rate-limit")
        void rateLimit() {
            throw new LalamoveRequestException(429,"ERR_RATE_LIMIT_EXCEEDED",null);
        }
        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Missing item");
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("internal details");
        }

        @GetMapping("/timeout")
        void timeout() {
            throw new QueryTimeoutException("canceling statement due to statement timeout");
        }
    }
}

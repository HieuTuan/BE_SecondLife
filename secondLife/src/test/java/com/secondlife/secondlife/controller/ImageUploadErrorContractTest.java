package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ErrorResponse;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.exception.ImageValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageUploadErrorContractTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new BoundaryController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void allImageErrorsRetainTheExistingResponseEnvelope() throws Exception {
        mvc.perform(post("/invalid-images"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.path").value("/invalid-images"))
                .andExpect(jsonPath("$.errors[0].field").value("images[0]"))
                .andExpect(jsonPath("$.errors[1].field").value("images[2]"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void multipartLimitUses413AndTheListingImagesField() throws Exception {
        mvc.perform(post("/api/v1/posts/init"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0].field").value("images"));
    }

    @Test
    void malformedMultipartHasA400InsteadOfAnInternalError() throws Exception {
        mvc.perform(post("/broken-multipart"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @RestController
    @TestComponent
    static class BoundaryController {
        @PostMapping("/invalid-images")
        void invalidImages() {
            throw new ImageValidationException(HttpStatus.BAD_REQUEST, List.of(
                    new ErrorResponse.FieldErrorItem("images[0]", "Ảnh quá nhỏ"),
                    new ErrorResponse.FieldErrorItem("images[2]", "Ảnh bị hỏng")));
        }

        @PostMapping("/api/v1/posts/init")
        void oversized() { throw new MaxUploadSizeExceededException(10485760); }

        @PostMapping("/broken-multipart")
        void malformed() { throw new MultipartException("Malformed boundary"); }
    }
}

package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.service.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private MediaController mediaController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mediaController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadImage_WhenValid_ShouldReturn201WithResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "front.jpg", "image/jpeg", "image-content".getBytes()
        );

        MediaUploadResponse response = new MediaUploadResponse(
                "https://res.cloudinary.com/dmcodhbcc/image/upload/v1/front.jpg",
                "secondlife/verifications/front",
                "jpg",
                1024L,
                "front.jpg"
        );

        when(mediaService.uploadImage(any(), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/media/upload")
                        .file(file)
                        .param("folder", "secondlife/verifications"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tải ảnh lên thành công"))
                .andExpect(jsonPath("$.data.url").value("https://res.cloudinary.com/dmcodhbcc/image/upload/v1/front.jpg"))
                .andExpect(jsonPath("$.data.publicId").value("secondlife/verifications/front"));
    }

    @Test
    void uploadMultipleImages_WhenValid_ShouldReturn201WithList() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "f1.jpg", "image/jpeg", "content1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "f2.jpg", "image/jpeg", "content2".getBytes());

        MediaUploadResponse resp1 = new MediaUploadResponse("https://url1.jpg", "id1", "jpg", 100L, "f1.jpg");
        MediaUploadResponse resp2 = new MediaUploadResponse("https://url2.jpg", "id2", "jpg", 200L, "f2.jpg");

        when(mediaService.uploadImages(any(), any())).thenReturn(List.of(resp1, resp2));

        mockMvc.perform(multipart("/api/v1/media/upload-multiple")
                        .file(file1)
                        .file(file2)
                        .param("folder", "secondlife/verifications"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].url").value("https://url1.jpg"))
                .andExpect(jsonPath("$.data[1].url").value("https://url2.jpg"));
    }
}

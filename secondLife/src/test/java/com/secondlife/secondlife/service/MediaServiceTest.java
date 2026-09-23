package com.secondlife.secondlife.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.service.impl.MediaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(cloudinary);
    }

    @Test
    void uploadImage_WhenValidFile_ShouldUploadAndReturnResponse() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "fake-image-bytes".getBytes()
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/dmcodhbcc/image/upload/v1/test.jpg",
                "public_id", "secondlife/verifications/test",
                "format", "jpg",
                "bytes", 1024L
        ));

        MediaUploadResponse response = mediaService.uploadImage(file, "secondlife/verifications");

        assertNotNull(response);
        assertEquals("https://res.cloudinary.com/dmcodhbcc/image/upload/v1/test.jpg", response.url());
        assertEquals("secondlife/verifications/test", response.publicId());
        assertEquals("jpg", response.format());
        assertEquals("test.jpg", response.originalFilename());
    }

    @Test
    void uploadImage_WhenEmptyFile_ShouldThrowBadRequestException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]
        );

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> mediaService.uploadImage(emptyFile, "folder"));
        assertTrue(ex.getMessage().contains("không được để trống"));
    }

    @Test
    void uploadImage_WhenInvalidContentType_ShouldThrowBadRequestException() {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello world".getBytes()
        );

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> mediaService.uploadImage(textFile, "folder"));
        assertTrue(ex.getMessage().contains("Định dạng file không hợp lệ"));
    }

    @Test
    void uploadImages_WhenValidList_ShouldUploadAll() throws IOException {
        MockMultipartFile file1 = new MockMultipartFile("files", "img1.png", "image/png", "img1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "img2.webp", "image/webp", "img2".getBytes());

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/dmcodhbcc/image/upload/v1/img.jpg",
                "public_id", "id1",
                "format", "png",
                "bytes", 500L
        ));

        List<MediaUploadResponse> responses = mediaService.uploadImages(List.of(file1, file2), "test-folder");

        assertEquals(2, responses.size());
        verify(uploader, times(2)).upload(any(byte[].class), anyMap());
    }
}

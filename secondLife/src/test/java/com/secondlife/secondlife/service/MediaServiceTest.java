package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.validation.ImageUploadValidator;
import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ImageValidationException;
import com.secondlife.secondlife.integration.storage.CloudinaryMediaStorage;
import com.secondlife.secondlife.service.impl.MediaServiceImpl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private MediaServiceImpl mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaServiceImpl(new CloudinaryMediaStorage(cloudinary), 10_485_760L,
                "secondlife/verifications", 104857600L, new ImageUploadValidator(600, 16000, 25000000, 2560), "64MB");
    }

    @Test
    void uploadImage_WhenValidFile_ShouldUploadAndReturnResponse() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", imageBytes("valid-front.jpg")
        );

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(call -> Map.of(
                "secure_url", "https://res.cloudinary.com/dmcodhbcc/image/upload/v1/test.jpg",
                "public_id", ((Map<?, ?>) call.getArgument(1)).get("folder") + "/" + ((Map<?, ?>) call.getArgument(1)).get("public_id"),
                "format", "jpg",
                "bytes", 1024L
        ));

        MediaUploadResponse response = mediaService.uploadImage(file, "secondlife/verifications");

        assertNotNull(response);
        assertEquals("https://res.cloudinary.com/dmcodhbcc/image/upload/v1/test.jpg", response.url());
        assertTrue(response.publicId().matches("secondlife/verifications/[0-9a-f-]{36}"));
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
        MockMultipartFile file1 = new MockMultipartFile("files", "img1.png", "image/png", imageBytes("valid-back.png"));
        MockMultipartFile file2 = new MockMultipartFile("files", "img2.webp", "image/webp", imageBytes("valid-label.webp"));

        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(call -> Map.of(
                "secure_url", "https://res.cloudinary.com/dmcodhbcc/image/upload/v1/img.jpg",
                "public_id", ((Map<?, ?>) call.getArgument(1)).get("folder") + "/" + ((Map<?, ?>) call.getArgument(1)).get("public_id"),
                "format", "png",
                "bytes", 500L
        ));

        List<MediaUploadResponse> responses = mediaService.uploadImages(List.of(file1, file2), "test-folder");

        assertEquals(2, responses.size());
        verify(uploader, times(2)).upload(any(byte[].class), anyMap());
    }

    @Test
    void configuredByteLimitRejectsOversizedImageBeforeUpload() {
        new ApplicationContextRunner()
                .withBean(Cloudinary.class, () -> cloudinary)
                .withBean(CloudinaryMediaStorage.class)
                .withBean(ImageUploadValidator.class)
                .withBean(MediaServiceImpl.class)
                .withPropertyValues("app.media.max-file-size-bytes=4", "app.media.default-folder=test-default")
                .run(context -> {
                    MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[5]);
                    assertThrows(BadRequestException.class,
                            () -> context.getBean(MediaServiceImpl.class).uploadImage(file, null));
                    verifyNoInteractions(cloudinary);
                });
    }

    @Test
    void configuredFolderIsUsedForAnImageAtTheByteLimit() throws IOException {
        when(cloudinary.uploader()).thenReturn(uploader);
        byte[] image = imageBytes("valid-front.jpg");
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            return Map.of("secure_url", "https://image.example/photo.jpg", "public_id", options.get("folder") + "/" + options.get("public_id"),
                    "format", "jpg", "bytes", 4L);
        });
        new ApplicationContextRunner()
                .withBean(Cloudinary.class, () -> cloudinary)
                .withBean(CloudinaryMediaStorage.class)
                .withBean(ImageUploadValidator.class)
                .withBean(MediaServiceImpl.class)
                .withPropertyValues("app.media.max-file-size-bytes=" + image.length, "app.media.default-folder=test-default")
                .run(context -> {
                    MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", image);
                    assertTrue(context.getBean(MediaServiceImpl.class).uploadImage(file, " ").publicId()
                            .matches("test-default/[0-9a-f-]{36}"));
                });
    }

    private byte[] imageBytes(String name) throws IOException {
        return Files.readAllBytes(Path.of("src/test/resources/image-validation", name));
    }

    @Test
    void invalidLastImageDoesNotUploadTheEarlierValidImage() throws Exception {
        var valid = new MockMultipartFile("files", "front.jpg", "image/jpeg", imageBytes("valid-front.jpg"));
        var corrupt = new MockMultipartFile("files", "broken.jpg", "image/jpeg", new byte[]{1, 2, 3});
        var failure = assertThrows(ImageValidationException.class,
                () -> mediaService.uploadImages(List.of(valid, corrupt), "avatars"));
        assertEquals("files[1]", failure.getErrors().getFirst().field());
        verifyNoInteractions(cloudinary);
    }

    @Test
    void normalizedBatchCannotRetainMoreThanTheConfiguredRequestBudget() throws Exception {
        var bounded = new MediaServiceImpl(new CloudinaryMediaStorage(cloudinary), 10485760,
                "avatars", 104857600, new ImageUploadValidator(600, 16000, 25000000, 2560), "40KB");
        byte[] jpeg = imageBytes("valid-front.jpg");
        byte[] webp = imageBytes("valid-label.webp");
        assertTrue(jpeg.length + webp.length < 40 * 1024);
        var failure = assertThrows(ImageValidationException.class, () -> bounded.uploadImages(List.of(
                new MockMultipartFile("files", "front.jpg", "image/jpeg", jpeg),
                new MockMultipartFile("files", "label.webp", "image/webp", webp)), "avatars"));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, failure.getStatus());
        assertEquals("files", failure.getErrors().getFirst().field());
        verifyNoInteractions(cloudinary);
    }
}

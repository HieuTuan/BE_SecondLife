package com.secondlife.secondlife.service;

import com.secondlife.secondlife.integration.storage.CloudinaryServiceImpl;

import com.cloudinary.Cloudinary;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudinaryImageUploadTest {
    @Test void persistsSecureUrlInsteadOfCloudinaryHttpUrl() throws Exception {
        var cloudinary = mock(Cloudinary.class, RETURNS_DEEP_STUBS);
        String path = "res.cloudinary.com/test/image/upload/photo.jpg";
        when(cloudinary.uploader().upload(any(), anyMap())).thenAnswer(invocation -> {
            Map<?, ?> options = invocation.getArgument(1);
            assertEquals("image", options.get("resource_type"));
            assertEquals(false, options.get("overwrite"));
            assertTrue(options.get("public_id").toString().matches("listings/images/[0-9a-f-]{36}"));
            return Map.of("url", "http://" + path, "secure_url", "https://" + path, "public_id", options.get("public_id"));
        });
        assertEquals("https://" + path, new CloudinaryServiceImpl(cloudinary).uploadImage(
                new MockMultipartFile("images", "photo.jpg", "image/jpeg", new byte[]{1,2,3})).secureUrl());
    }

    @Test void missingSecureUrlFailsInsteadOfPersistingHttp() throws Exception {
        var cloudinary = mock(Cloudinary.class, RETURNS_DEEP_STUBS);
        when(cloudinary.uploader().upload(any(), anyMap())).thenReturn(Map.of("url", "http://res.cloudinary.com/test/image/upload/photo.jpg"));
        assertThrows(IOException.class, () -> new CloudinaryServiceImpl(cloudinary).uploadImage(
                new MockMultipartFile("images", "photo.jpg", "image/jpeg", new byte[]{1,2,3})));
    }
}

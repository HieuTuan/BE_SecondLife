package com.secondlife.secondlife.service;

import com.cloudinary.Cloudinary;
import com.secondlife.secondlife.service.impl.CloudinaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.IOException;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CloudinaryImageUploadTest {
    @Test void persistsSecureUrlInsteadOfCloudinaryHttpUrl() throws Exception {
        var cloudinary = mock(Cloudinary.class, RETURNS_DEEP_STUBS);
        String path = "res.cloudinary.com/test/image/upload/photo.jpg";
        when(cloudinary.uploader().upload(any(), anyMap())).thenReturn(Map.of("url", "http://" + path, "secure_url", "https://" + path));
        assertEquals("https://" + path, new CloudinaryServiceImpl(cloudinary).uploadImage(
                new MockMultipartFile("images", "photo.jpg", "image/jpeg", new byte[]{1,2,3})));
    }

    @Test void missingSecureUrlFailsInsteadOfPersistingHttp() throws Exception {
        var cloudinary = mock(Cloudinary.class, RETURNS_DEEP_STUBS);
        when(cloudinary.uploader().upload(any(), anyMap())).thenReturn(Map.of("url", "http://res.cloudinary.com/test/image/upload/photo.jpg"));
        assertThrows(IOException.class, () -> new CloudinaryServiceImpl(cloudinary).uploadImage(
                new MockMultipartFile("images", "photo.jpg", "image/jpeg", new byte[]{1,2,3})));
    }
}

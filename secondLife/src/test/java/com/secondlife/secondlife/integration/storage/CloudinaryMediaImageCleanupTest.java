package com.secondlife.secondlife.integration.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryMediaImageCleanupTest {
    @Test
    void timeoutCleansTheAttemptedImageUsingItsGeneratedId() throws Exception {
        var cloud = mock(Cloudinary.class);
        var uploader = mock(Uploader.class);
        when(cloud.uploader()).thenReturn(uploader);
        AtomicReference<String> attemptedId = new AtomicReference<>();
        IOException original = new IOException("Provider timeout after accepting bytes");
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(call -> {
            Map<?, ?> options = call.getArgument(1);
            attemptedId.set(options.get("folder") + "/" + options.get("public_id"));
            throw original;
        });
        assertSame(original, assertThrows(IOException.class,
                () -> new CloudinaryMediaStorage(cloud).uploadImage(new byte[]{1}, "avatars")));
        verify(uploader).destroy(eq(attemptedId.get()), anyMap());
    }

    @Test
    void invalidProviderReferenceCleansOnlyTheExpectedIdAndPreservesTheFailure() throws Exception {
        var cloud = mock(Cloudinary.class);
        var uploader = mock(Uploader.class);
        when(cloud.uploader()).thenReturn(uploader);
        AtomicReference<String> attemptedId = new AtomicReference<>();
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(call -> {
            Map<?, ?> options = call.getArgument(1);
            attemptedId.set(options.get("folder") + "/" + options.get("public_id"));
            return Map.of("public_id", "unrelated-existing-image", "secure_url", "https://image.example/photo.jpg",
                    "format", "jpg", "bytes", 1L);
        });
        doThrow(new IOException("Cleanup unavailable")).when(uploader).destroy(any(), anyMap());
        assertThrows(IOException.class, () -> new CloudinaryMediaStorage(cloud).uploadImage(new byte[]{1}, "avatars"));
        verify(uploader).destroy(eq(attemptedId.get()), anyMap());
    }

    @Test
    void successReturnsTheVerifiedSecureReference() throws Exception {
        var cloud = mock(Cloudinary.class);
        var uploader = mock(Uploader.class);
        when(cloud.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(call -> {
            Map<?, ?> options = call.getArgument(1);
            return Map.of("public_id", options.get("folder") + "/" + options.get("public_id"),
                    "secure_url", "https://image.example/photo.jpg", "format", "jpg", "bytes", 1L);
        });
        assertEquals("https://image.example/photo.jpg", new CloudinaryMediaStorage(cloud).uploadImage(new byte[]{1}, "avatars").secureUrl());
    }
}

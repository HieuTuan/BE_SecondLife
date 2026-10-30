package com.secondlife.secondlife.integration.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class CloudinaryMediaStorage implements MediaStorage {
    private final Cloudinary cloudinary;

    @Override
    public UploadResult uploadImage(byte[] content, String folder) throws IOException {
        String targetFolder = folder == null ? "" : folder.replaceAll("/+$", "");
        String name = UUID.randomUUID().toString();
        String expectedId = targetFolder.isBlank() ? name : targetFolder + "/" + name;
        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "folder", targetFolder, "resource_type", "image", "public_id", name,
                    "overwrite", false, "allowed_formats", List.of("jpg", "png", "webp")));
            if (result == null || !expectedId.equals(result.get("public_id"))
                    || !(result.get("secure_url") instanceof String url) || !url.startsWith("https://")) {
                throw new IOException("Cloudinary did not return the expected secure image reference");
            }
            return uploadResult(result);
        } catch (IOException | RuntimeException ex) {
            try {
                deleteImage(expectedId);
            } catch (IOException | RuntimeException cleanupFailure) {
                log.warn("Could not clean failed media image {}", expectedId);
            }
            throw ex;
        }
    }

    @Override
    public void deleteImage(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
    }

    @Override
    public UploadResult uploadVideo(byte[] content, String folder) throws IOException {
        return uploadResult(cloudinary.uploader().upload(content, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "video"
        )));
    }

    @Override
    public void deleteVideo(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "video"));
    }

    private UploadResult uploadResult(Map<?, ?> result) {
        return new UploadResult((String) result.get("secure_url"), (String) result.get("public_id"),
                (String) result.get("format"), result.get("bytes") instanceof Number n ? n.longValue() : null);
    }
}

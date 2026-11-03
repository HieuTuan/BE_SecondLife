package com.secondlife.secondlife.integration.storage;

import com.secondlife.secondlife.service.CloudinaryService;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    @Override
    public UploadedImage uploadImage(MultipartFile file) throws IOException {
        String publicId = "listings/images/" + UUID.randomUUID();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "resource_type", "image", "public_id", publicId, "overwrite", false,
                    "allowed_formats", List.of("jpg", "png", "webp")));
            if (!(result.get("secure_url") instanceof String url) || !url.startsWith("https://")
                    || !publicId.equals(result.get("public_id"))) {
                throw new IOException("Cloudinary did not return the expected secure image reference");
            }
            return new UploadedImage(url, publicId);
        } catch (IOException | RuntimeException ex) {
            try {
                deleteImage(publicId);
            } catch (IOException | RuntimeException cleanupFailure) {
                log.warn("Could not clean failed image upload {}", publicId);
            }
            throw ex;
        }
    }

    @Override
    public void deleteImage(String publicId) throws IOException {
        cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image", "invalidate", true));
    }
}

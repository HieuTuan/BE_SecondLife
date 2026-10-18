package com.secondlife.secondlife.integration.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CloudinaryMediaStorage implements MediaStorage {
    private final Cloudinary cloudinary;

    @Override
    public UploadResult uploadImage(byte[] content, String folder) throws IOException {
        return uploadResult(cloudinary.uploader().upload(content, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto"
        )));
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

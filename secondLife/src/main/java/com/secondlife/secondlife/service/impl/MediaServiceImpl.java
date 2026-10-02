package com.secondlife.secondlife.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );
    private static final String DEFAULT_FOLDER = "secondlife/verifications";

    private final Cloudinary cloudinary;

    @Override
    public MediaUploadResponse uploadImage(MultipartFile file, String folder) {
        validateImageFile(file);

        String targetFolder = (folder != null && !folder.isBlank()) ? folder.trim() : DEFAULT_FOLDER;

        try {
            log.info("Uploading image [{}] of size {} bytes to Cloudinary folder [{}]",
                    file.getOriginalFilename(), file.getSize(), targetFolder);

            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", targetFolder,
                    "resource_type", "auto"
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            String format = (String) uploadResult.get("format");
            long bytes = uploadResult.get("bytes") instanceof Number n ? n.longValue() : file.getSize();

            log.info("Successfully uploaded image to Cloudinary: publicId={}, url={}", publicId, secureUrl);

            return new MediaUploadResponse(
                    secureUrl,
                    publicId,
                    format,
                    bytes,
                    file.getOriginalFilename()
            );
        } catch (IOException e) {
            log.error("Failed to upload image [{}] to Cloudinary: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new IllegalStateException("Lỗi khi tải ảnh lên dịch vụ lưu trữ: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MediaUploadResponse> uploadImages(List<MultipartFile> files, String folder) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Danh sách file không được để trống");
        }

        List<MediaUploadResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            responses.add(uploadImage(file, folder));
        }
        return responses;
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BadRequestException("Dung lượng file vượt quá giới hạn tối đa cho phép (10MB)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Định dạng file không hợp lệ. Chỉ chấp nhận các định dạng ảnh: JPEG, PNG, WEBP");
        }
    }
}

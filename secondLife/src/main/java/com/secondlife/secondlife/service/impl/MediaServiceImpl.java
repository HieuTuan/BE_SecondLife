package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.integration.storage.MediaStorage;
import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.service.MediaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/quicktime",
            "video/webm"
    );
    private final MediaStorage storage;
    private final long maxFileSizeBytes;
    private final String defaultFolder;
    private final long maxVideoSizeBytes;

    @Autowired
    public MediaServiceImpl(MediaStorage storage,
            @Value("${app.media.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.media.default-folder}") String defaultFolder,
            @Value("${app.media.max-video-size-bytes:104857600}") long maxVideoSizeBytes) {
        this.storage = storage;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.defaultFolder = defaultFolder;
        this.maxVideoSizeBytes = maxVideoSizeBytes;
    }

    public MediaServiceImpl(MediaStorage storage,
            long maxFileSizeBytes,
            String defaultFolder) {
        this(storage, maxFileSizeBytes, defaultFolder, 104857600L);
    }

    @Override
    public MediaUploadResponse uploadImage(MultipartFile file, String folder) {
        validateImageFile(file);

        String targetFolder = (folder != null && !folder.isBlank()) ? folder.trim() : defaultFolder;

        try {
            log.info("Uploading image [{}] of size {} bytes to Cloudinary folder [{}]",
                    file.getOriginalFilename(), file.getSize(), targetFolder);

            var uploadResult = storage.uploadImage(file.getBytes(), targetFolder);

            String secureUrl = uploadResult.secureUrl();
            String publicId = uploadResult.publicId();
            String format = uploadResult.format();
            long bytes = uploadResult.bytes() != null ? uploadResult.bytes() : file.getSize();

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

    @Override
    public MediaUploadResponse uploadVideo(MultipartFile file, String folder) {
        validateVideoFile(file);

        String targetFolder = (folder != null && !folder.isBlank()) ? folder.trim() : "secondlife/inspections/videos";

        try {
            log.info("Uploading video [{}] of size {} bytes to Cloudinary folder [{}]",
                    file.getOriginalFilename(), file.getSize(), targetFolder);

            var uploadResult = storage.uploadVideo(file.getBytes(), targetFolder);

            String secureUrl = uploadResult.secureUrl();
            String publicId = uploadResult.publicId();
            String format = uploadResult.format();
            long bytes = uploadResult.bytes() != null ? uploadResult.bytes() : file.getSize();

            log.info("Successfully uploaded video to Cloudinary: publicId={}, url={}", publicId, secureUrl);

            return new MediaUploadResponse(
                    secureUrl,
                    publicId,
                    format,
                    bytes,
                    file.getOriginalFilename()
            );
        } catch (IOException e) {
            log.error("Failed to upload video [{}] to Cloudinary: {}", file.getOriginalFilename(), e.getMessage(), e);
            throw new IllegalStateException("Lỗi khi tải video lên dịch vụ lưu trữ: " + e.getMessage(), e);
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File tải lên không được để trống");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("Dung lượng file vượt quá giới hạn tối đa cho phép (" + maxFileSizeBytes + " bytes)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Định dạng file không hợp lệ. Chỉ chấp nhận các định dạng ảnh: JPEG, PNG, WEBP");
        }
    }

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File video tải lên không được để trống");
        }

        if (file.getSize() > maxVideoSizeBytes) {
            throw new BadRequestException("Dung lượng video vượt quá giới hạn tối đa cho phép (" + (maxVideoSizeBytes / (1024 * 1024)) + " MB)");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException("Định dạng video không hợp lệ. Chỉ chấp nhận các định dạng video: MP4, MOV (quicktime), WEBM");
        }
    }
}

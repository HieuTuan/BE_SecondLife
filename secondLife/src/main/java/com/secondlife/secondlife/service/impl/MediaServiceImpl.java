package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.ErrorResponse;
import com.secondlife.secondlife.common.validation.ImageUploadValidator;
import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ImageValidationException;
import com.secondlife.secondlife.integration.storage.MediaStorage;
import com.secondlife.secondlife.service.MediaService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class MediaServiceImpl implements MediaService {

    private static final Set<String> ALLOWED_VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/quicktime",
            "video/webm"
    );
    private final MediaStorage storage;
    private final long maxFileSizeBytes;
    private final String defaultFolder;
    private final long maxVideoSizeBytes;
    private final ImageUploadValidator imageValidator;
    private final long maxImageBatchBytes;

    public MediaServiceImpl(MediaStorage storage,
            @Value("${app.media.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.media.default-folder}") String defaultFolder,
            @Value("${app.media.max-video-size-bytes:104857600}") long maxVideoSizeBytes,
            ImageUploadValidator imageValidator,
            @Value("${spring.servlet.multipart.max-request-size:64MB}") String maxRequestSize) {
        this.storage = storage;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.defaultFolder = defaultFolder;
        this.maxVideoSizeBytes = maxVideoSizeBytes;
        this.imageValidator = imageValidator;
        this.maxImageBatchBytes = DataSize.parse(maxRequestSize).toBytes();
        if (maxImageBatchBytes <= 0) throw new IllegalArgumentException("Multipart request size must bound image batches");
    }

    @Override
    public MediaUploadResponse uploadImage(MultipartFile file, String folder) {
        var prepared = imageValidator.prepareMediaImage(file, "file", maxFileSizeBytes);
        return storeImage(file, prepared, folder);
    }

    private MediaUploadResponse storeImage(MultipartFile original, ImageUploadValidator.PreparedImage prepared, String folder) {
        String targetFolder = (folder != null && !folder.isBlank()) ? folder.trim() : defaultFolder;

        try {
            log.info("Uploading prepared image of size {} bytes", prepared.file().getSize());

            var uploadResult = storage.uploadImage(prepared.file().getBytes(), targetFolder);

            String secureUrl = uploadResult.secureUrl();
            String publicId = uploadResult.publicId();
            String format = uploadResult.format();
            long bytes = uploadResult.bytes() != null ? uploadResult.bytes() : prepared.file().getSize();

            log.info("Successfully uploaded image to Cloudinary: publicId={}, url={}", publicId, secureUrl);

            return new MediaUploadResponse(
                    secureUrl,
                    publicId,
                    format,
                    bytes,
                    original.getOriginalFilename()
            );
        } catch (IOException e) {
            log.warn("Image storage upload failed");
            throw new IllegalStateException("Lỗi khi tải ảnh lên dịch vụ lưu trữ", e);
        }
    }

    @Override
    public List<MediaUploadResponse> uploadImages(List<MultipartFile> files, String folder) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("Danh sách file không được để trống");
        }

        List<MediaUploadResponse> responses = new ArrayList<>();
        List<ImageUploadValidator.PreparedImage> prepared = new ArrayList<>();
        long preparedBytes = 0;
        for (int index = 0; index < files.size(); index++) {
            var image = imageValidator.prepareMediaImage(files.get(index), "files[" + index + "]", maxFileSizeBytes);
            preparedBytes += image.file().getSize();
            if (preparedBytes > maxImageBatchBytes) {
                throw new ImageValidationException(HttpStatus.PAYLOAD_TOO_LARGE, List.of(new ErrorResponse.FieldErrorItem(
                        "files", "Tổng dung lượng ảnh sau chuẩn hóa vượt giới hạn " + maxImageBatchBytes + " byte. Hãy chia nhỏ lần tải lên.")));
            }
            prepared.add(image);
        }
        try {
            for (int index = 0; index < files.size(); index++) {
                responses.add(storeImage(files.get(index), prepared.get(index), folder));
            }
        } catch (RuntimeException ex) {
            for (var response : responses) {
                try {
                    storage.deleteImage(response.publicId());
                } catch (IOException | RuntimeException cleanupFailure) {
                    log.warn("Could not clean image {}", response.publicId());
                }
            }
            throw ex;
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

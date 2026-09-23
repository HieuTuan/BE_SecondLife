package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media Storage", description = "Media and file upload APIs via Cloudinary")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a single image file (JPG, PNG, WEBP) to Cloudinary and return CDN URL")
    public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadImage(
            @Parameter(description = "Image file to upload", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Target folder on Cloudinary (default: secondlife/verifications)")
            @RequestParam(value = "folder", required = false, defaultValue = "secondlife/verifications") String folder
    ) {
        MediaUploadResponse response = mediaService.uploadImage(file, folder);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tải ảnh lên thành công", response));
    }

    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload multiple image files at once to Cloudinary")
    public ResponseEntity<ApiResponse<List<MediaUploadResponse>>> uploadMultipleImages(
            @Parameter(description = "List of image files to upload", required = true)
            @RequestParam("files") List<MultipartFile> files,
            @Parameter(description = "Target folder on Cloudinary (default: secondlife/verifications)")
            @RequestParam(value = "folder", required = false, defaultValue = "secondlife/verifications") String folder
    ) {
        List<MediaUploadResponse> response = mediaService.uploadImages(files, folder);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tải danh sách ảnh lên thành công", response));
    }
}

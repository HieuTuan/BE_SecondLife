package com.secondlife.secondlife.dto.response;

public record MediaUploadResponse(
        String url,
        String publicId,
        String format,
        long bytes,
        String originalFilename
) {
}

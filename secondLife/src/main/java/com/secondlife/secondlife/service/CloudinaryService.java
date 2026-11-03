package com.secondlife.secondlife.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface CloudinaryService {
    record UploadedImage(String secureUrl, String publicId) { }

    UploadedImage uploadImage(MultipartFile file) throws IOException;

    void deleteImage(String publicId) throws IOException;
}

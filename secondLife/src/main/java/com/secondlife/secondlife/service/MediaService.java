package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MediaService {

    MediaUploadResponse uploadImage(MultipartFile file, String folder);

    List<MediaUploadResponse> uploadImages(List<MultipartFile> files, String folder);
}

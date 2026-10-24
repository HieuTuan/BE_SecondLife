package com.secondlife.secondlife.integration.storage;

import java.io.IOException;

public interface MediaStorage {
    record UploadResult(String secureUrl, String publicId, String format, Long bytes) { }

    UploadResult uploadImage(byte[] content, String folder) throws IOException;
    UploadResult uploadVideo(byte[] content, String folder) throws IOException;
    void deleteVideo(String publicId) throws IOException;
}

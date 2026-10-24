package com.secondlife.secondlife.exception;

public class VideoUploadException extends RuntimeException {
    private final int status;

    public VideoUploadException(String message, int status) {
        super(message);
        if (status != 429 && status != 502 && status != 503) {
            throw new IllegalArgumentException("Unsupported video upload failure status");
        }
        this.status = status;
    }

    public int getStatus() { return status; }
}

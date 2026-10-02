package com.secondlife.secondlife.service.ekyc.vnpt;

public class VnptUploadException extends VnptApiException {
    public VnptUploadException(VnptApiException cause) {
        super("VNPT file upload failed", cause.getUpstreamStatus(), cause.getEndpoint(), cause.getProviderCode());
    }
}

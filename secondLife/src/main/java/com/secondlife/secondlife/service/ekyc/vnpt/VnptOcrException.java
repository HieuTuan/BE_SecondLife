package com.secondlife.secondlife.service.ekyc.vnpt;

public class VnptOcrException extends VnptApiException {
    public VnptOcrException(VnptApiException cause) {
        super("VNPT OCR failed", cause.getUpstreamStatus(), cause.getEndpoint(), cause.getProviderCode());
    }
}

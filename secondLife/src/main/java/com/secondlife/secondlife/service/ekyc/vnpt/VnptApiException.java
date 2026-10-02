package com.secondlife.secondlife.service.ekyc.vnpt;

public class VnptApiException extends RuntimeException {
    private final int upstreamStatus;
    private final String endpoint;
    private final String providerCode;

    public VnptApiException(String message, int upstreamStatus, String endpoint, String providerCode) {
        super(message);
        this.upstreamStatus = upstreamStatus;
        this.endpoint = endpoint;
        this.providerCode = providerCode;
    }

    public int getUpstreamStatus() { return upstreamStatus; }
    public String getEndpoint() { return endpoint; }
    public String getProviderCode() { return providerCode; }
}

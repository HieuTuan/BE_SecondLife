package com.secondlife.secondlife.integration.ekyc.vnpt;

public class VnptApiException extends RuntimeException {
    private final int upstreamStatus;
    private final String endpoint;
    private final String providerCode;
    private final String providerDetail;

    public VnptApiException(String message, int upstreamStatus, String endpoint, String providerCode) {
        this(message, upstreamStatus, endpoint, providerCode, null);
    }

    public VnptApiException(String message, int upstreamStatus, String endpoint,
                            String providerCode, String providerDetail) {
        super(message);
        this.upstreamStatus = upstreamStatus;
        this.endpoint = endpoint;
        this.providerCode = providerCode;
        this.providerDetail = providerDetail;
    }

    public int getUpstreamStatus() { return upstreamStatus; }
    public String getEndpoint() { return endpoint; }
    public String getProviderCode() { return providerCode; }
    public String getProviderDetail() { return providerDetail; }
}

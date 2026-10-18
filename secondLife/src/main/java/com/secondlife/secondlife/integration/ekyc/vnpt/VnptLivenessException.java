package com.secondlife.secondlife.integration.ekyc.vnpt;

public class VnptLivenessException extends VnptApiException {
    public VnptLivenessException(VnptApiException cause) {
        super("VNPT liveness check failed", cause.getUpstreamStatus(), cause.getEndpoint(), cause.getProviderCode());
    }
}

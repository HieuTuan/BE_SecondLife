package com.secondlife.secondlife.service.ekyc.vnpt;

public class VnptCompareException extends VnptApiException {
    public VnptCompareException(VnptApiException cause) {
        super("VNPT face comparison failed", cause.getUpstreamStatus(), cause.getEndpoint(), cause.getProviderCode());
    }
}

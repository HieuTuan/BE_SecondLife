package com.secondlife.secondlife.service.ekyc.vnpt;

public class VnptAuthenticationException extends VnptApiException {
    public VnptAuthenticationException(int status, String endpoint, String code) {
        super("VNPT authentication failed", status, endpoint, code);
    }
}

package com.secondlife.secondlife.exception;

public class ShippingProviderException extends RuntimeException {
    private final int upstreamStatus;

    public ShippingProviderException(String message) {
        this(message, 502);
    }

    public ShippingProviderException(String message, int upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public int getUpstreamStatus() {
        return upstreamStatus;
    }
}

package com.secondlife.secondlife.exception;

public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException() { super("Could not send the seller email verification code. Please try again later."); }
}

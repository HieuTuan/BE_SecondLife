package com.secondlife.secondlife.exception;

import java.util.Set;

/** Retains diagnostic codes and field names, never provider messages or request values. */
public final class LalamoveRequestException extends ShippingProviderException {
    private static final Set<String> KNOWN_CODES=Set.of(
        "ERR_REQUIRED_FIELD","ERR_MISSING_FIELD","ERR_INVALID_FIELD","ERR_INVALID_QUOTATION_ID",
        "ERR_INSUFFICIENT_CREDIT","ERR_RATE_LIMIT_EXCEEDED","ERR_INVALID_PHONE_NUMBER",
        "ERR_INSUFFICIENT_STOPS","ERR_TOO_MANY_STOPS","ERR_INVALID_MARKET","ERR_INVALID_SERVICE_TYPE",
        "ERR_OUT_OF_SERVICE_AREA","ERR_INVALID_SCHEDULE_TIME","ERR_REVERSE_GEOCODE_FAILURE",
        "ERR_INVALID_SPECIAL_REQUEST","ERR_INVALID_RESPONSE","ERR_ORDER_NOT_FOUND");
    private final String errorCode;
    private final String invalidField;

    public LalamoveRequestException(int status,String code,String field) {
        super(message(status,safeCode(code),safeField(field)),status);
        errorCode=safeCode(code);invalidField=safeField(field);
    }
    public String getErrorCode() { return errorCode; }
    public String getInvalidField() { return invalidField; }
    private static String safeCode(String code) { return code!=null && KNOWN_CODES.contains(code)?code:null; }
    private static String safeField(String field) {
        return field!=null && field.matches("(?:data\\.)?(?:sender\\.(?:name|phone|stopId)|recipients(?:\\[[0-9]{1,2}\\])?\\.(?:name|phone|stopId|remarks)|quotationId|isPODEnabled|partner|metadata|market|name|phone|stopId)")?field:null;
    }
    private static String message(int status,String code,String field) {
        return "Lalamove request failed (HTTP "+status+(code==null?"":", "+code)+(field==null?"":", field="+field)+")";
    }
}

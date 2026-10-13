package com.secondlife.secondlife.service.shipping;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class LalamoveSignatures {
    private LalamoveSignatures() { }

    public static String sign(String secret, String timestamp, String method, String path, String body) {
        if (secret == null || secret.isEmpty() || timestamp == null || method == null || path == null || body == null)
            throw new IllegalArgumentException("Lalamove signing inputs are required");
        String raw = timestamp + "\r\n" + method + "\r\n" + path + "\r\n\r\n" + body;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Lalamove signing is unavailable");
        }
    }

    public static boolean verify(String expected, String supplied) {
        return expected != null && supplied != null && expected.matches("[0-9a-f]{64}") && supplied.matches("[0-9a-f]{64}")
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }
}

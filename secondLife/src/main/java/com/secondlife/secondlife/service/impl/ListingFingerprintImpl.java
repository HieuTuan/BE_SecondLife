package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.ListingFingerprint;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

@Service("listingFingerprint")
public class ListingFingerprintImpl implements ListingFingerprint {
    @Override
    public String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
    @Override
    public String sha256(String text) { return sha256(text.getBytes(StandardCharsets.UTF_8)); }
    @Override
    public String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}

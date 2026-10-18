package com.secondlife.secondlife.service;

public interface ListingFingerprint {
    String normalize(String text);
    String sha256(String text);
    String sha256(byte[] bytes);
}

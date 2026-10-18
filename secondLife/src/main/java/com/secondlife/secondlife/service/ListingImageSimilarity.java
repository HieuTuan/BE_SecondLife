package com.secondlife.secondlife.service;

public interface ListingImageSimilarity {
    String fingerprint(byte[] bytes);
    boolean similar(String first, String second);
}

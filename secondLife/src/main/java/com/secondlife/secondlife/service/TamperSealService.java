package com.secondlife.secondlife.service;

public interface TamperSealService {
    String generateSealCode();

    boolean verifyChecksum(String sealCode);

    boolean isValidSealFormat(String sealCode);

}

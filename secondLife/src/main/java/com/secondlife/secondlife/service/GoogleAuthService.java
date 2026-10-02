package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.GoogleUserInfo;

public interface GoogleAuthService {
    GoogleUserInfo verifyToken(String idToken);
}

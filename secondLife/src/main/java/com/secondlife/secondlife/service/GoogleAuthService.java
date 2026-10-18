package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.GoogleUserInfo;

public interface GoogleAuthService {
    GoogleUserInfo verifyToken(String idToken);
}

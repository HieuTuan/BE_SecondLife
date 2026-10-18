package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.EkycRequest;
import com.secondlife.secondlife.dto.response.EkycResult;

public interface EkycService {

    EkycResult verifyIdentity(EkycRequest request);
}

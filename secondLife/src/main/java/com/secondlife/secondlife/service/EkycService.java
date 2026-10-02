package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;

public interface EkycService {

    EkycResult verifyIdentity(EkycRequest request);
}

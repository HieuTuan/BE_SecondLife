package com.secondlife.secondlife.service.ekyc;

import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;

public interface EkycProviderClient {

    EkycResult verify(EkycRequest request);

    String getProviderName();
}

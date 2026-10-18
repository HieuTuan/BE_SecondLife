package com.secondlife.secondlife.integration.ekyc;

import com.secondlife.secondlife.dto.request.EkycRequest;
import com.secondlife.secondlife.dto.response.EkycResult;

public interface EkycProviderClient {

    EkycResult verify(EkycRequest request);

    String getProviderName();
}

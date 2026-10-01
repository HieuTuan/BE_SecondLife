package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptMaskFaceClient {
    private static final String PATH = "/ai/v1/web/face/mask";
    private final VnptHttpClient httpClient;

    public VnptResults.Envelope<VnptResults.MaskFace> check(String selfieHash,
                                                              String clientSession, String token) {
        try {
            return VnptResponseMapper.envelope(httpClient.postJson(PATH, Map.of(
                    "token", token, "client_session", clientSession, "img", selfieHash)),
                    PATH, VnptResponseMapper::mask);
        } catch (VnptAuthenticationException ex) { throw ex; }
        catch (VnptApiException ex) { throw new VnptLivenessException(ex); }
    }
}

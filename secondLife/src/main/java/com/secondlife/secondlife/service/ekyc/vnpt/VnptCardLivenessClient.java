package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptCardLivenessClient {
    private static final String PATH = "/ai/v1/web/card/liveness";
    private final VnptHttpClient httpClient;

    public VnptResults.Envelope<VnptResults.CardLiveness> check(String frontHash,
                                                                  String clientSession, String token) {
        try {
            return VnptResponseMapper.envelope(httpClient.postJson(PATH, Map.of(
                    "token", token, "client_session", clientSession,
                    "crop_param", "0,0", "img", frontHash)), PATH, VnptResponseMapper::card);
        } catch (VnptAuthenticationException ex) { throw ex; }
        catch (VnptApiException ex) { throw new VnptLivenessException(ex); }
    }
}

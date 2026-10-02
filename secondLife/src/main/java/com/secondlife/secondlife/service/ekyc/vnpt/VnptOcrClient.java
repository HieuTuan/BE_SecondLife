package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptOcrClient {
    private static final String PATH = "/ai/v1/web/ocr/id";
    private final VnptHttpClient httpClient;

    public VnptResults.Envelope<VnptResults.Ocr> analyze(String frontHash, String backHash,
                                                           String clientSession, String token, int type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("img_front", frontHash);
        body.put("img_back", backHash);
        body.put("client_session", clientSession);
        body.put("type", type);
        body.put("validate_postcode", true);
        // Match the Web SDK 3.2.1.0 OCR request for its captured document images.
        body.put("crop_param", "0,0");
        body.put("token", token);
        try {
            return VnptResponseMapper.envelope(httpClient.postJson(PATH, body), PATH, VnptResponseMapper::ocr);
        } catch (VnptAuthenticationException ex) { throw ex; }
        catch (VnptApiException ex) { throw new VnptOcrException(ex); }
    }
}

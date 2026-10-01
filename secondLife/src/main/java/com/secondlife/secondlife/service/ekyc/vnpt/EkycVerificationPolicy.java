package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class EkycVerificationPolicy {
    public Decision decide(VnptResults.Envelope<VnptResults.Ocr> ocr,
                           VnptResults.Envelope<VnptResults.CardLiveness> card,
                           VnptResults.Envelope<VnptResults.FaceLiveness> face,
                           VnptResults.Envelope<VnptResults.MaskFace> mask,
                           VnptResults.Envelope<VnptResults.FaceCompare> compare) {
        if (!success(ocr) || ocr.object().id() == null || ocr.object().id().isBlank()) {
            return new Decision(false, "OCR_INCOMPLETE");
        }
        if (!success(card) || !"success".equalsIgnoreCase(card.object().liveness())
                || Boolean.TRUE.equals(card.object().fakePrintPhoto())) {
            return new Decision(false, "CARD_LIVENESS_FAILED");
        }
        if (!success(face) || !"success".equalsIgnoreCase(face.object().liveness())
                || multiple(face.object().multipleFacesDetails())) {
            return new Decision(false, "FACE_LIVENESS_FAILED");
        }
        if (!success(mask) || !"no".equalsIgnoreCase(mask.object().masked())) {
            return new Decision(false, "FACE_MASKED");
        }
        if (!success(compare) || !"MATCH".equalsIgnoreCase(compare.object().msg())
                || Boolean.TRUE.equals(compare.object().multipleFaces())
                || multiple(compare.object().multipleFacesDetails())) {
            return new Decision(false, "FACE_MISMATCH");
        }
        return new Decision(true, "PASSED");
    }

    private boolean success(VnptResults.Envelope<?> response) {
        return response != null && "IDG-00000000".equals(response.message())
                && (response.statusCode() == null || response.statusCode() == 200);
    }

    private boolean multiple(VnptResults.MultipleFacesDetails details) {
        return details != null && (Boolean.TRUE.equals(details.multipleFace1())
                || Boolean.TRUE.equals(details.multipleFace2()));
    }

    public record Decision(boolean verified, String reasonCode) { }
}

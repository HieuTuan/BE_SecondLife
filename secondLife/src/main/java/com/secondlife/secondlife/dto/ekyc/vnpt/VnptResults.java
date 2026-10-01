package com.secondlife.secondlife.dto.ekyc.vnpt;

import java.util.List;
import java.util.UUID;

public final class VnptResults {
    private VnptResults() { }

    public record Envelope<T>(String message, List<String> errors, Integer statusCode,
                              T object, String dataSign) { }

    public record Ocr(String msg, String msgBack, Integer typeId, Integer backTypeId,
                      String cardType, String id, String name, String birthDay,
                      String nationality, String gender, String validDate,
                      String originLocation, String recentLocation, String issueDate,
                      List<String> generalWarning) { }

    public record CardLiveness(String liveness, String livenessMsg, Boolean faceSwapping,
                               Boolean fakeLiveness, Double faceSwappingProb,
                               Double fakeLivenessProb, Boolean fakePrintPhoto,
                               Double fakePrintPhotoProb) { }

    public record MultipleFacesDetails(Boolean multipleFace1, Boolean multipleFace2) { }

    public record FaceLiveness(String liveness, String livenessMsg, Double livenessProb,
                               String blurFace, Double blurFaceScore, String isEyeOpen,
                               String backgroundWarning, MultipleFacesDetails multipleFacesDetails) { }

    public record MaskFace(String masked) { }

    public record FaceCompare(String result, String msg, Double prob, Boolean multipleFaces,
                              MultipleFacesDetails multipleFacesDetails) { }

    public record Verification(UUID verificationId, boolean verified, String reasonCode,
                               Envelope<Ocr> ocr, Envelope<CardLiveness> cardLiveness,
                               Envelope<FaceLiveness> faceLiveness,
                               Envelope<MaskFace> maskFace,
                               Envelope<FaceCompare> faceCompare) { }
}

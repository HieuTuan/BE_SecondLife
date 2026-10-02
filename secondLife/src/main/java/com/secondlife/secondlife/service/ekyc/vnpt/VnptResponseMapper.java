package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class VnptResponseMapper {
    private VnptResponseMapper() { }

    public static <T> VnptResults.Envelope<T> envelope(JsonNode root, String path,
                                                       Function<JsonNode, T> mapper) {
        JsonNode object = root.path("object");
        if (object.isMissingNode() || object.isNull() || !object.isObject()) {
            throw new VnptApiException("Malformed VNPT response", 502, path, "MISSING_OBJECT");
        }
        Integer statusCode = root.has("statusCode") ? root.path("statusCode").asInt() : null;
        return new VnptResults.Envelope<>(text(root, "message"), strings(root.path("errors")),
                statusCode, mapper.apply(object), text(root, "dataSign"));
    }

    public static VnptResults.Ocr ocr(JsonNode node) {
        return new VnptResults.Ocr(text(node,"msg"), text(node,"msg_back"), integer(node,"type_id"),
                integer(node,"back_type_id"), text(node,"card_type"), text(node,"id"),
                text(node,"name"), text(node,"birth_day"), text(node,"nationality"),
                text(node,"gender"), text(node,"valid_date"), text(node,"origin_location"),
                text(node,"recent_location"), text(node,"issue_date"),
                strings(node.path("general_warning")));
    }

    public static VnptResults.CardLiveness card(JsonNode node) {
        return new VnptResults.CardLiveness(text(node,"liveness"), text(node,"liveness_msg"),
                bool(node,"face_swapping"), bool(node,"fake_liveness"),
                number(node,"face_swapping_prob"), number(node,"fake_liveness_prob"),
                bool(node,"fake_print_photo"), number(node,"fake_print_photo_prob"));
    }

    public static VnptResults.FaceLiveness face(JsonNode node) {
        return new VnptResults.FaceLiveness(text(node,"liveness"), text(node,"liveness_msg"),
                number(node,"liveness_prob"), text(node,"blur_face"), number(node,"blur_face_score"),
                text(node,"is_eye_open"), text(node,"background_warning"),
                multipleFaces(node.path("multiple_faces_details")));
    }

    public static VnptResults.MaskFace mask(JsonNode node) {
        return new VnptResults.MaskFace(text(node,"masked"));
    }

    public static VnptResults.FaceCompare compare(JsonNode node) {
        return new VnptResults.FaceCompare(text(node,"result"), text(node,"msg"),
                number(node,"prob"), bool(node,"multiple_faces"),
                multipleFaces(node.path("multiple_faces_details")));
    }

    private static VnptResults.MultipleFacesDetails multipleFaces(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        return new VnptResults.MultipleFacesDetails(bool(node,"multiple_face_1"),
                bool(node,"multiple_face_2"));
    }

    private static String text(JsonNode node, String key) {
        JsonNode child = node.path(key);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private static Integer integer(JsonNode node, String key) {
        JsonNode child = node.path(key);
        return child.isMissingNode() || child.isNull() ? null : child.asInt();
    }

    private static Double number(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isMissingNode() || child.isNull()) return null;
        try { return Double.valueOf(child.asText()); }
        catch (NumberFormatException ex) { return null; }
    }

    private static Boolean bool(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isMissingNode() || child.isNull()) return null;
        String value = child.asText();
        if ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value) || "fail".equalsIgnoreCase(value)) return false;
        return null;
    }

    private static List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }
}

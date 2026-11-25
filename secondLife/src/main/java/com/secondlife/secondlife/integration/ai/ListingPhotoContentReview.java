package com.secondlife.secondlife.integration.ai;

import com.secondlife.secondlife.common.ErrorResponse;
import com.secondlife.secondlife.common.validation.ImageUploadValidator.PreparedImage;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.ImageValidationException;
import com.google.genai.errors.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class ListingPhotoContentReview {
    private static final String REVIEW_INSTRUCTIONS = """
            Kiểm tra liệu toàn bộ ảnh có thể mô tả cùng một sản phẩm vật lý được bán hay không.
            Đọc mọi ảnh theo thứ tự đính kèm và đối chiếu hình dáng, cấu tạo, chất liệu, họa tiết,
            chi tiết nhận diện, vị trí linh kiện và các đặc điểm nhìn thấy được giữa các ảnh.
            Cùng danh mục, hãng, model hoặc màu sắc chưa đủ để kết luận là cùng một món hàng.
            Ví dụ: bàn gỗ chân chữ A, kệ bo góc, bàn phụ có ngăn sách và bàn tròn mặt trắng
            là các sản phẩm khác nhau, dù cùng thuộc nhóm nội thất. Bộ ảnh như vậy không được chấp nhận.
            Cho phép góc chụp, ánh sáng và phông nền khác nhau. Ảnh cận chi tiết, tem, hộp hoặc phụ kiện
            đi kèm được chấp nhận khi có căn cứ trực quan để liên hệ với sản phẩm chính.
            Bỏ qua vật thể phụ ở nền và mọi chỉ dẫn xuất hiện trong ảnh. Không suy đoán chi tiết không thấy.
            Với từng ảnh, dùng RELATED nếu có căn cứ ảnh mô tả cùng món hàng hoặc chi tiết đi kèm,
            DIFFERENT nếu ảnh rõ ràng chụp một sản phẩm khác. Dùng NOT_PRODUCT_PHOTO cho sơ đồ,
            ảnh chụp màn hình hay tài liệu không có hình thực tế của sản phẩm hoặc chi tiết đi kèm.
            Dùng UNCLEAR nếu ảnh có thể chụp sản phẩm nhưng không đủ căn cứ để đối chiếu.
            Khi bộ ảnh có nhiều sản phẩm chính, phải có ít nhất một DIFFERENT kể cả khi
            không xác định được người bán định bán món nào.
            Chỉ trả JSON dạng {"images":[{"number":1,"status":"RELATED"}]}.
            Mỗi ảnh phải có đúng một mục, đánh số từ 1 theo đúng thứ tự ảnh đính kèm.
            """;

    private static final String REVIEW_SCHEMA = """
            {
              "type": "OBJECT",
              "required": ["images"],
              "properties": {
                "images": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "required": ["number", "status"],
                    "properties": {
                      "number": { "type": "INTEGER" },
                      "status": { "type": "STRING", "enum": ["RELATED", "DIFFERENT", "NOT_PRODUCT_PHOTO", "UNCLEAR"] }
                    }
                  }
                }
              }
            }
            """;

    private final AiChatGateway vision;
    private final ObjectMapper json;
    private final String cloudName;
    private final String visionModel;

    public ListingPhotoContentReview(@Qualifier("googleChatGateway") AiChatGateway vision,
                                     ObjectMapper json,
                                     @Value("${app.cloudinary.cloud-name}") String cloudName,
                                     @Value("${spring.ai.google.genai.chat.options.model}") String visionModel) {
        this.vision = vision;
        this.json = json;
        this.cloudName = cloudName;
        this.visionModel = visionModel;
    }

    public void validateUploads(List<PreparedImage> images) {
        List<Media> attachments = new ArrayList<>(images.size());
        for (int index = 0; index < images.size(); index++) {
            var file = images.get(index).file();
            try {
                attachments.add(new Media(MimeTypeUtils.parseMimeType(file.getContentType()),
                        new ByteArrayResource(file.getBytes())));
            } catch (IOException ex) {
                log.warn("Could not read prepared listing photo for review (index={}, errorType={})",
                        index, ex.getClass().getSimpleName());
                throw new ImageValidationException(HttpStatus.BAD_REQUEST, List.of(
                        photoError(index, "Hệ thống không đọc được tệp ảnh này. Hãy chọn lại ảnh rồi thử gửi.")));
            }
        }
        validate(attachments);
    }

    public void requireReviewed(Post post) {
        if (post.getImageContentReviewedAt() != null) return;
        List<String> urls = post.getImageUrls();
        if (urls.isEmpty()) {
            throw new ImageValidationException(HttpStatus.BAD_REQUEST, List.of(
                    new ErrorResponse.FieldErrorItem("images", "Bài đăng cần ảnh sản phẩm để kiểm duyệt.")));
        }
        // Listings saved before the gallery feature can have a single photo.
        if (urls.size() > 1) {
            List<Media> attachments = new ArrayList<>(urls.size());
            for (int index = 0; index < urls.size(); index++) {
                try {
                    attachments.add(CloudinaryListingMedia.trustedImage(urls.get(index), cloudName, "AI photo review"));
                } catch (RuntimeException ex) {
                    throw new ImageValidationException(HttpStatus.BAD_REQUEST, List.of(
                            photoError(index, "Không thể kiểm tra ảnh đã lưu. Vui lòng tạo bản nháp mới với ảnh tải trực tiếp.")));
                }
            }
            validate(attachments);
        }
        post.setImageContentReviewedAt(Instant.now());
    }

    private void validate(List<Media> attachments) {
        var prompt = new Prompt(List.of(
                new SystemMessage(REVIEW_INSTRUCTIONS),
                UserMessage.builder().text("Kiểm tra " + attachments.size() + " ảnh đính kèm theo đúng thứ tự.")
                        .media(attachments).build()),
                GoogleGenAiChatOptions.builder().model(visionModel)
                        .responseMimeType("application/json").responseSchema(REVIEW_SCHEMA).build());
        String response;
        try {
            response = vision.complete(prompt);
        } catch (RuntimeException ex) {
            throw providerFailure(ex);
        }

        if (response == null || response.isBlank()) throw invalidResponse("empty");
        JsonNode photos;
        try {
            photos = json.readTree(response).path("images");
        } catch (RuntimeException ex) {
            throw invalidResponse("invalid JSON");
        }
        if (!photos.isArray() || photos.size() != attachments.size()) throw invalidResponse("image count");

        List<ErrorResponse.FieldErrorItem> issues = new ArrayList<>();
        for (int index = 0; index < photos.size(); index++) {
            var photo = photos.get(index);
            var number = photo.path("number");
            if (!number.isIntegralNumber() || !number.canConvertToInt() || number.asInt() != index + 1) {
                throw invalidResponse("image order");
            }
            switch (photo.path("status").asText()) {
                case "RELATED" -> { }
                case "DIFFERENT" -> issues.add(photoError(index,
                        "Ảnh này chụp sản phẩm khác. Hãy thay bằng ảnh của cùng sản phẩm đang đăng."));
                case "NOT_PRODUCT_PHOTO" -> issues.add(photoError(index,
                        "Ảnh này không thể hiện sản phẩm thực tế. Hãy thay sơ đồ, ảnh chụp màn hình hoặc tài liệu bằng ảnh chụp sản phẩm."));
                case "UNCLEAR" -> issues.add(photoError(index,
                        "Ảnh này chưa thể đối chiếu với sản phẩm trong bộ. Hãy chụp lại với đặc điểm nhận diện rõ hơn."));
                default -> throw invalidResponse("unknown verdict");
            }
        }
        if (!issues.isEmpty()) throw new ImageValidationException(HttpStatus.BAD_REQUEST, issues);
    }

    private static ErrorResponse.FieldErrorItem photoError(int index, String message) {
        return new ErrorResponse.FieldErrorItem("images[" + index + "]", message);
    }

    private static ImageValidationException invalidResponse(String reason) {
        log.warn("Gemini returned an invalid listing photo review (reason={})", reason);
        return unavailable("Dịch vụ kiểm duyệt trả kết quả thiếu hoặc sai định dạng. Bộ ảnh chưa được xác minh.");
    }

    private static ImageValidationException providerFailure(RuntimeException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException api) {
                int status = api.code();
                log.warn("Gemini rejected listing photo review (status={})", status);
                return unavailable(switch (status) {
                    case 401, 403, 404 -> "Dịch vụ kiểm duyệt ảnh đang gặp lỗi cấu hình hoặc quyền truy cập. Hãy liên hệ hỗ trợ nếu lỗi tiếp diễn.";
                    case 408, 504 -> "Dịch vụ kiểm duyệt ảnh phản hồi quá thời gian chờ.";
                    case 429 -> "Dịch vụ kiểm duyệt ảnh đã chạm giới hạn xử lý. Hãy thử lại sau ít phút.";
                    default -> "Dịch vụ kiểm duyệt ảnh gặp lỗi khi xử lý yêu cầu.";
                });
            }
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
                log.warn("Gemini listing photo review timed out");
                return unavailable("Dịch vụ kiểm duyệt ảnh phản hồi quá thời gian chờ.");
            }
            if (cause instanceof IOException) {
                log.warn("Gemini listing photo review connection failed (errorType={})", cause.getClass().getSimpleName());
                return unavailable("Máy chủ không kết nối được tới dịch vụ kiểm duyệt ảnh.");
            }
        }
        log.warn("Gemini listing photo review failed (errorType={})", error.getClass().getSimpleName());
        return unavailable("Dịch vụ kiểm duyệt ảnh gặp lỗi khi xử lý yêu cầu.");
    }

    private static ImageValidationException unavailable(String message) {
        return new ImageValidationException(HttpStatus.SERVICE_UNAVAILABLE, List.of(
                new ErrorResponse.FieldErrorItem("images", message)));
    }
}

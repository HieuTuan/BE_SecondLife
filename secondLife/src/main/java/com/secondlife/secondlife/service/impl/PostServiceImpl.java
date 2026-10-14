package com.secondlife.secondlife.service.impl;



import com.secondlife.secondlife.dto.request.AiChatRequest;

import com.secondlife.secondlife.dto.request.PostInitRequest;

import com.secondlife.secondlife.dto.response.AiChatResponse;

import com.secondlife.secondlife.dto.response.AiPriceEstimationResponse;

import com.secondlife.secondlife.dto.response.PostInitResponse;

import com.secondlife.secondlife.dto.response.PostSubmitResponse;

import com.secondlife.secondlife.entity.CategoryQuestionTemplate;

import com.secondlife.secondlife.entity.InspectionOrder;

import com.secondlife.secondlife.entity.Post;

import com.secondlife.secondlife.entity.User;

import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;

import com.secondlife.secondlife.repository.CategoryRepository;

import com.secondlife.secondlife.repository.InspectionOrderRepository;

import com.secondlife.secondlife.repository.ItemRepository;

import com.secondlife.secondlife.repository.PostRepository;

import com.secondlife.secondlife.repository.UserRepository;

import com.secondlife.secondlife.exception.ForbiddenException;

import com.secondlife.secondlife.exception.NotFoundException;

import com.secondlife.secondlife.service.AiChatService;

import com.secondlife.secondlife.service.ListingAccessService;

import com.secondlife.secondlife.service.ListingCreditService;

import com.secondlife.secondlife.service.ListingFingerprint;

import com.secondlife.secondlife.service.ListingImageSimilarity;

import com.secondlife.secondlife.enums.CreditType;

import com.secondlife.secondlife.exception.BadRequestException;

import com.secondlife.secondlife.exception.ConflictException;

import com.secondlife.secondlife.exception.AiProviderException;

import com.secondlife.secondlife.service.PostService;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.chat.messages.UserMessage;

import org.springframework.ai.chat.prompt.Prompt;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



import java.math.BigDecimal;

import java.util.UUID;



@lombok.extern.slf4j.Slf4j
@Service
public class PostServiceImpl implements PostService {



    private final BigDecimal inspectionFee;

    private final BigDecimal shippingFee;

    private final int maxUnfinishedDrafts;

    private final long maxImageBytes;

    private final int minImages;

    private final int maxImages;

    private final ListingImageSimilarity imageSimilarity;



    private final PostRepository postRepository;

    private final UserRepository userRepository;

    private final ListingCreditService listingCredits;

    private final ListingAccessService listingAccess;

    private final AiChatService aiChatService;

    private final CategoryQuestionTemplateRepository templateRepository;

    private final com.secondlife.secondlife.repository.AiChatSessionRepository sessionRepository;

    private final CategoryRepository categoryRepository;

    private final ItemRepository itemRepository;

    private final ChatClient chatClient;

    private final com.secondlife.secondlife.service.CloudinaryService cloudinaryService;

    private final InspectionOrderRepository inspectionOrderRepository;

    private final org.springframework.ai.chat.model.ChatModel googleChatModel;

    private final com.secondlife.secondlife.repository.AiChatMessageRepository chatMessageRepository;

    private final com.secondlife.secondlife.service.ListingPublicationService publication;

    private final com.secondlife.secondlife.service.ListingReviewService reviews;

    private final com.secondlife.secondlife.service.ListingSubmissionLock submissionLock;



    public PostServiceImpl(PostRepository postRepository,

                           UserRepository userRepository,

                           ListingCreditService listingCredits,

                           ListingAccessService listingAccess,

                           AiChatService aiChatService,

                           CategoryQuestionTemplateRepository templateRepository,

                           com.secondlife.secondlife.repository.AiChatSessionRepository sessionRepository,

                           com.secondlife.secondlife.repository.AiChatMessageRepository chatMessageRepository,

                           CategoryRepository categoryRepository,

                           ItemRepository itemRepository,

                           ChatClient.Builder chatClientBuilder,

                           com.secondlife.secondlife.service.CloudinaryService cloudinaryService,

                           InspectionOrderRepository inspectionOrderRepository,

                           @org.springframework.beans.factory.annotation.Qualifier("googleGenAiChatModel")

                           org.springframework.ai.chat.model.ChatModel googleChatModel,

                           com.secondlife.secondlife.service.ListingPublicationService publication,

                           com.secondlife.secondlife.service.ListingReviewService reviews,

                           com.secondlife.secondlife.service.ListingSubmissionLock submissionLock,

                           ListingImageSimilarity imageSimilarity,

                           @Value("${app.inspection.inspection-fee}") BigDecimal inspectionFee,

                           @Value("${app.inspection.shipping-fee}") BigDecimal shippingFee,

                           @Value("${app.listing.max-unfinished-drafts}") int maxUnfinishedDrafts,

                           @Value("${app.listing.max-image-bytes}") long maxImageBytes,

                           @Value("${app.listing.min-images}") int minImages,

                           @Value("${app.listing.max-images}") int maxImages) {

        if (inspectionFee.signum() < 0 || shippingFee.signum() < 0)

            throw new IllegalArgumentException("Inspection and shipping fees must be non-negative");

        if (maxUnfinishedDrafts <= 0 || maxImageBytes <= 0)

            throw new IllegalArgumentException("Listing draft and image byte limits must be positive");

        if (minImages <= 0 || maxImages < minImages)

            throw new IllegalArgumentException("Listing image bounds require a positive minimum and maximum at least the minimum");

        this.postRepository = postRepository;

        this.userRepository = userRepository;

        this.listingCredits = listingCredits;

        this.listingAccess = listingAccess;

        this.aiChatService = aiChatService;

        this.templateRepository = templateRepository;

        this.sessionRepository = sessionRepository;

        this.categoryRepository = categoryRepository;

        this.itemRepository = itemRepository;

        this.chatClient = chatClientBuilder.build();

        this.cloudinaryService = cloudinaryService;

        this.inspectionOrderRepository = inspectionOrderRepository;

        this.googleChatModel = googleChatModel;

        this.chatMessageRepository = chatMessageRepository;

        this.publication = publication;

        this.reviews = reviews;

        this.submissionLock = submissionLock;

        this.imageSimilarity = imageSimilarity;

        this.inspectionFee = inspectionFee;

        this.shippingFee = shippingFee;

        this.maxUnfinishedDrafts = maxUnfinishedDrafts;

        this.maxImageBytes = maxImageBytes;

        this.minImages = minImages;

        this.maxImages = maxImages;

    }



    @Override

    @Transactional

    public PostInitResponse initPost(UUID userId, PostInitRequest request) {

        User user = userRepository.findByIdForRoleUpdate(userId)

                .orElseThrow(() -> new NotFoundException("User not found"));

        if (request.getCategoryId() == null || !categoryRepository.existsById(request.getCategoryId()))

            throw new BadRequestException("A valid categoryId is required");

        if (request.getItemId() != null) {

            var item = itemRepository.findById(request.getItemId()).orElseThrow(() -> new BadRequestException("Invalid itemId"));

            if (!request.getCategoryId().equals(item.getCategory().getId()))

                throw new BadRequestException("itemId does not belong to categoryId");

        }

        if (postRepository.countByUser_IdAndStatusIn(userId, java.util.List.of("DRAFT", "REJECTED", "PENDING", "PENDING_INSPECTION")) >= maxUnfinishedDrafts)

            throw new ConflictException("Maximum " + maxUnfinishedDrafts + " unfinished drafts; finish an existing draft first");

        if (request.getImages() == null || request.getImages().size() < minImages || request.getImages().size() > maxImages)

            throw new BadRequestException("Upload between " + minImages + " and " + maxImages + " product images");

        for (var image : request.getImages()) {

            if (image == null || image.isEmpty() || image.getSize() > maxImageBytes || image.getContentType() == null

                    || !java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(image.getContentType()))

                throw new BadRequestException("Every image must be a non-empty JPEG, PNG or WebP no larger than " + maxImageBytes + " bytes");

        }



        Post post = new Post();

        post.setUser(user);

        post.setCategoryId(request.getCategoryId());

        post.setItemId(request.getItemId());

        post.setStatus("DRAFT");

        

        for (var image : request.getImages()) {

            try {

                byte[] imageBytes = image.getBytes();

                String perceptualFingerprint = imageSimilarity.fingerprint(imageBytes);

                String imageUrl = cloudinaryService.uploadImage(image);

                if (imageUrl == null || imageUrl.isBlank()) throw new IllegalStateException("Image upload returned an empty URL");

                post.getImages().add(new com.secondlife.secondlife.entity.PostImage(imageUrl,

                        ListingFingerprint.sha256(imageBytes), perceptualFingerprint));

            } catch (java.io.IOException e) {

                throw new RuntimeException("Failed to upload image to Cloudinary", e);

            } catch (IllegalArgumentException e) {

                throw new BadRequestException("Invalid or excessively large image dimensions");

            }

        }



        post.setImageUrl(post.getImages().getFirst().getImageUrl());

        post.setImageFingerprint(post.getImages().getFirst().getImageFingerprint());



        post = postRepository.save(post);



        // Extract names early
        final String categoryName = categoryRepository.findById(request.getCategoryId())
                .map(com.secondlife.secondlife.entity.Category::getName).orElse("Không xác định");
        final String itemName = request.getItemId() != null ?
                itemRepository.findById(request.getItemId())
                    .map(com.secondlife.secondlife.entity.Item::getName).orElse("Không xác định")
                : "Không xác định";

        boolean isAiMode = request.getMode() == null || !"manual".equalsIgnoreCase(request.getMode().trim());

        if (isAiMode) {
            // Deduct 1 AI_CHAT credit immediately in an autonomous transaction because AI Vision scan consumes costly GPU/LLM resources
            String chatCreditKey = "AI_INIT_SCAN:" + post.getId() + ":" + UUID.randomUUID();
            listingCredits.consumeImmediately(userId, CreditType.AI_CHAT, chatCreditKey);

            // 3. Call AI to analyze image
            AiChatRequest aiRequest = new AiChatRequest();
            aiRequest.setPostId(post.getId());

            // Prompt for AI Vision: Check match between images and selected category/item/brand
            String selectedCategoryName = categoryRepository.findById(request.getCategoryId()).orElseThrow().getName();
            String selectedItemName = request.getItemId() == null ? "Không xác định" : itemRepository.findById(request.getItemId()).orElseThrow().getName();
            String declaredBrand = request.getBrand() != null && !request.getBrand().isBlank() ? request.getBrand().trim() : null;

            StringBuilder visionCheckPrompt = new StringBuilder();
            visionCheckPrompt.append("Người bán đang đăng bài với thông tin khai báo ở Bước 1:\n");
            visionCheckPrompt.append(String.format("- Danh mục sản phẩm đã chọn: \"%s\"\n", selectedCategoryName));
            visionCheckPrompt.append(String.format("- Loại thiết bị/sản phẩm đã chọn: \"%s\"\n", selectedItemName));
            if (declaredBrand != null) {
                visionCheckPrompt.append(String.format("- Thương hiệu/Hãng sản xuất người bán đã nhập: \"%s\"\n", declaredBrand));
            }

            visionCheckPrompt.append("""

                    NHIỆM VỤ BẮT BUỘC (KIỂM TRA HÌNH ẢNH SẢN PHẨM):
                    Hãy quan sát thật kỹ toàn bộ các hình ảnh sản phẩm đính kèm để thẩm định 2 tiêu chuẩn:
                    """);

            visionCheckPrompt.append(String.format("""
                    TIÊU CHUẨN 1 - LOẠI SẢN PHẨM:
                    - Nếu sản phẩm trong ảnh KHÔNG PHẢI là loại "%s" (hoặc không phù hợp với danh mục "%s"):
                      (Ví dụ: Người bán chọn loại "%s" nhưng hình ảnh thực tế lại là thiết bị khác như "Quạt", "Máy giặt", "Nồi chiên", "Bếp", "Bàn ăn", "Tủ lạnh"...):
                      -> BẮT BUỘC TRẢ VỀ CÂU TRẢ LỜI BẮT ĐẦU BẰNG TIỀN TỐ:
                      [CẢNH_BÁO_KHÔNG_KHỚP_LOAI]
                      Sau đó nêu rõ:
                      - Tên sản phẩm thực tế mà bạn nhìn thấy trong ảnh là gì.
                      - Khẳng định rõ ràng sản phẩm thực tế trong ảnh không khớp với loại sản phẩm "%s" mà người bán đã chọn.
                      - Yêu cầu người bán quay lại Bước 1 để chọn đúng Loại sản phẩm hoặc tải lại hình ảnh đúng của "%s".
                      TUYỆT ĐỐI KHÔNG viết mô tả bán hàng nếu hình ảnh sai loại sản phẩm.
                    """, selectedItemName, selectedCategoryName, selectedItemName, selectedItemName, selectedItemName));

            if (declaredBrand != null) {
                visionCheckPrompt.append(String.format("""
                    TIÊU CHUẨN 2 - THƯƠNG HIỆU / HÃNG SẢN XUẤT:
                    - Nếu sản phẩm trong ảnh đúng là loại "%s", hãy quan sát logo, tên thương hiệu in trên thân sản phẩm, nhãn mác hoặc vỏ hộp:
                    - Nếu trong ảnh NHÌN THẤY RÕ RÀNG thương hiệu thực tế của sản phẩm là một hãng KHÁC BIỆT HOÀN TOÀN so với hãng mà người bán đã nhập ("%s"):
                      (Ví dụ: Người bán nhập "%s" nhưng trên thân sản phẩm in rõ logo/chữ "Tefal", "Sunhouse", "Panasonic", "Philips", "Kangaroo", "Sharp", "Cuckoo", "Electrolux"...):
                      -> BẮT BUỘC TRẢ VỀ CÂU TRẢ LỜI BẮT ĐẦU BẰNG TIỀN TỐ:
                      [CẢNH_BÁO_KHÔNG_KHỚP_HANG: <Tên_Hãng_Thực_Tế_Trong_Ảnh>]
                      Sau đó nêu rõ:
                      - Tên thương hiệu thực tế mà bạn nhìn thấy rõ ràng trên sản phẩm trong ảnh là gì.
                      - Khẳng định thương hiệu trên sản phẩm trong ảnh khác với thương hiệu "%s" mà người bán đã nhập.
                      - Yêu cầu người bán kiểm tra lại để đổi thương hiệu thành hãng trong ảnh hoặc tải lên hình ảnh đúng của hãng "%s".
                      LƯU Ý: Nếu trên ảnh không thể nhìn rõ thương hiệu (ảnh chụp góc khuất, chữ in mờ, hoặc không có logo) thì KHÔNG được cảnh báo sai hãng, hãy coi như hợp lệ. Chỉ cảnh báo khi NHÌN THẤY RÕ mâu thuẫn thương hiệu!
                    """, selectedItemName, declaredBrand, declaredBrand, declaredBrand, declaredBrand));
            }

            visionCheckPrompt.append(String.format("""
                    TIÊU CHUẨN 3 - NẾU HÌNH ẢNH HỢP LỆ (Đúng loại "%s"%s):
                    -> BẮT ĐẦU BẰNG:
                    [HỢP_LỆ]
                    Sau đó nhận xét ngắn gọn, khách quan về ngoại quan, màu sắc, tình trạng và các đặc điểm nhìn thấy được của sản phẩm để phục vụ đăng bán. Chỉ mô tả sản phẩm nhìn thấy, không suy đoán hãng hay tuổi đời nếu ảnh không chứng minh. Không đề xuất giá, không thêm lời chào, không tự đặt câu hỏi.
                    """, selectedItemName, declaredBrand != null ? " và đúng/không mâu thuẫn hãng " + declaredBrand : ""));

            aiRequest.setMessage(visionCheckPrompt.toString());
            aiRequest.setImages(request.getImages());

            AiChatResponse aiResponse = aiChatService.processChat(aiRequest, userId);
            String aiReply = aiResponse.getReply() != null ? aiResponse.getReply().trim() : "";

            // 1. Kiểm tra không khớp loại sản phẩm
            boolean isTypeMismatch = aiReply.contains("[CẢNH_BÁO_KHÔNG_KHỚP_LOAI]")
                    || aiReply.contains("[CẢNH BÁO KHÔNG KHỚP LOẠI]")
                    || aiReply.contains("[CẢNH_BÁO_KHÔNG_KHỚP]")
                    || aiReply.contains("[CẢNH BÁO KHÔNG KHỚP]")
                    || (!aiReply.contains("[HỢP_LỆ]") && !aiReply.contains("[CẢNH_BÁO_KHÔNG_KHỚP_HANG]") && !aiReply.contains("[CẢNH BÁO KHÔNG KHỚP HÃNG]") && (
                            aiReply.toLowerCase().contains("không khớp với loại")
                            || aiReply.toLowerCase().contains("không phải là loại")
                            || aiReply.toLowerCase().contains("khác với loại")
                       ));

            // 2. Kiểm tra không khớp thương hiệu
            boolean isBrandMismatch = aiReply.contains("[CẢNH_BÁO_KHÔNG_KHỚP_HANG")
                    || aiReply.contains("[CẢNH BÁO KHÔNG KHỚP HÃNG")
                    || (!aiReply.contains("[HỢP_LỆ]") && (
                            aiReply.toLowerCase().contains("không khớp với thương hiệu")
                            || aiReply.toLowerCase().contains("khác với thương hiệu")
                            || aiReply.toLowerCase().contains("thương hiệu thực tế trong ảnh là")
                            || aiReply.toLowerCase().contains("thương hiệu thực tế là")
                       ));

            if (isTypeMismatch) {
                String cleanReason = aiReply
                        .replaceAll("\\[CẢNH_BÁO[^\\]]*\\]", "")
                        .replaceAll("\\[CẢNH BÁO[^\\]]*\\]", "")
                        .replace("[KHÔNG_PHÙ_HỢP]", "")
                        .replace("[KHÔNG PHÙ HỢP]", "")
                        .trim();
                log.warn("Post init blocked: Image does not match category/item. Selected: {} / {}, Reason: {}",
                        selectedCategoryName, selectedItemName, cleanReason);
                postRepository.delete(post);
                throw new BadRequestException("HÌNH ẢNH KHÔNG KHỚP LOẠI SẢN PHẨM: " + cleanReason);
            }

            if (isBrandMismatch) {
                String detectedBrand = "";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[CẢNH_BÁO_KHÔNG_KHỚP_HANG:\\s*([^\\]]+)\\]").matcher(aiReply);
                if (m.find()) {
                    detectedBrand = m.group(1).trim();
                }
                String cleanReason = aiReply
                        .replaceAll("\\[CẢNH_BÁO[^\\]]*\\]", "")
                        .replaceAll("\\[CẢNH BÁO[^\\]]*\\]", "")
                        .trim();
                log.warn("Post init blocked: Image does not match brand. Declared: {}, Detected: {}, Reason: {}",
                        declaredBrand, detectedBrand, cleanReason);
                postRepository.delete(post);
                String brandPrefix = detectedBrand.isEmpty() ? "" : "[DETECTED_BRAND:" + detectedBrand + "] ";
                throw new BadRequestException("HÌNH ẢNH KHÔNG KHỚP THƯƠNG HIỆU: " + brandPrefix + cleanReason);
            }

            // Nếu hợp lệ: làm sạch tiền tố [HỢP_LỆ]
            String cleanDescription = aiReply.replaceFirst("^\\[HỢP_LỆ\\]\\s*", "").trim();
            post.setAiDescription(cleanDescription);
            // QUAN TRỌNG: description bài đăng để trống khi vừa khởi tạo.
            // Chỉ lưu kết quả thẩm định ảnh vào aiDescription, KHÔNG tự gán văn bản thô vào description bài đăng.
            post.setDescription(null);
            post.setDescriptionAccepted(false);

            // 4. Fetch or Generate Template:
            CategoryQuestionTemplate template = templateRepository
                    .findByCategoryIdAndItemId(request.getCategoryId(), request.getItemId())
                    .orElse(null);

            boolean isLegacyTemplate = template != null && template.getTemplateText() != null && (
                    template.getTemplateText().toLowerCase().contains("thời gian bạn mua") ||
                    template.getTemplateText().toLowerCase().contains("thời gian mua") ||
                    template.getTemplateText().toLowerCase().contains("thời gian đã sử dụng") ||
                    template.getTemplateText().toLowerCase().contains("thời gian sử dụng") ||
                    template.getTemplateText().toLowerCase().contains("giá mua mới") ||
                    template.getTemplateText().toLowerCase().contains("giá gốc") ||
                    template.getTemplateText().toLowerCase().contains("năm bạn mua") ||
                    template.getTemplateText().toLowerCase().contains("mua từ khi nào") ||
                    !template.getTemplateText().contains("\n") ||
                    template.getTemplateText().lines().count() <= 1
            );

            if (template == null || isLegacyTemplate) {
                String promptText = String.format("""
                        Bạn là chuyên gia thẩm định đồ gia dụng cũ hàng đầu tại thị trường Việt Nam.
                        Hãy tạo một bộ câu hỏi khảo sát ngắn gọn gồm đúng 4 câu hỏi bằng tiếng Việt dành riêng cho loại sản phẩm '%s' (thuộc danh mục '%s').
                        Bộ câu hỏi này phục vụ mục đích: Giúp người bán cung cấp đầy đủ thông tin thực tế để AI ĐỊNH GIÁ CHÍNH XÁC NHẤT và TỔNG HỢP MÔ TẢ ĐĂNG BÁN.

                        LƯU Ý CỰC KỲ QUAN TRỌNG:
                        Người bán ĐÃ CUNG CẤP CÁC THÔNG TIN sau ở bước trước:
                        - Tên danh mục và Loại sản phẩm
                        - Thương hiệu / Hãng sản xuất
                        - Năm mua
                        - Giá sản phẩm lúc mua (VNĐ)
                        TUYỆT ĐỐI KHÔNG ĐƯỢC HỎI LẠI bất kỳ thông tin nào ở trên (không hỏi năm mua, không hỏi thời gian mua, không hỏi giá mua mới/giá gốc, không hỏi thương hiệu/hãng).

                        CẤU TRÚC ĐÚNG 4 CÂU HỎI BẮT BUỘC:
                        PHẦN 1: CÁC CÂU HỎI CỐT LÕI VỀ HIỆN TRẠNG SẢN PHẨM (PHỤC VỤ ĐỊNH GIÁ KHẤU HAO):
                        1. Tần suất và mức độ sử dụng thực tế của bạn? (Phân biệt hàng lướt like-new mới dùng vài lần cất hộp với hàng dùng thường xuyên hằng ngày).
                        2. Hiện trạng ngoại quan và phụ kiện đi kèm? (Độ mới 99%% hay có trầy xước, cấn móp; fullbox đủ phụ kiện hay thiếu phụ kiện gì).
                        3. Tình trạng hoạt động kỹ thuật và bảo hành hãng? (Vận hành nguyên bản chạy êm chưa sửa chữa, có lỗi chức năng nào không, còn bảo hành chính hãng không).

                        PHẦN 2: CÂU HỎI BỔ SUNG CHI TIẾT ĐẶC THÙ CHO RIÊNG LOẠI '%s':
                        4. Thông số kỹ thuật, dung tích, công suất hoặc phiên bản đặc thù của '%s'? (Giúp định vị chính xác phân khúc máy).

                        YÊU CẦU ĐẶC BIỆT VỀ CÂU TRẢ LỜI GỢI Ý (RẤT QUAN TRỌNG):
                        - Mỗi câu hỏi BẮT BUỘC phải đi kèm phần gợi ý trả lời trong ngoặc đơn dạng: `(Ví dụ: gợi ý 1, gợi ý 2, gợi ý 3, gợi ý 4)`.
                        - ĐẶC BIỆT đối với loại sản phẩm '%s', các gợi ý trả lời ở câu 3 (hoạt động/kỹ thuật) và câu 4 (thông số kỹ thuật) PHẢI ĐƯỢC THIẾT KẾ RIÊNG BIỆT, ĐÚNG BẢN CHẤT KỸ THUẬT VÀ PHÂN KHÚC THỰC TẾ của '%s' tại Việt Nam:
                          * Ví dụ nếu là Máy giặt: gợi ý câu 4 về khối lượng giặt kg, cửa trước/cửa trên, Inverter (Ví dụ: 8.5kg cửa trước Inverter, 7kg cửa trên, 9kg cửa ngang, 10kg giặt sấy); gợi ý câu 3 về vắt êm không rung lắc, motor chạy êm.
                          * Ví dụ nếu là Tủ lạnh: gợi ý câu 4 về dung tích lít, số cánh, Inverter (Ví dụ: mini 90L, 180-250L 2 cánh Inverter, trên 400L Side-by-side); gợi ý câu 3 về làm lạnh sâu làm đá nhanh, không đóng tuyết.
                          * Ví dụ nếu là Nồi chiên không dầu: gợi ý câu 4 về dung tích lít, bảng điều khiển (Ví dụ: 5.5 lít cơ núm vặn, 8 lít cảm ứng có kính nhìn, 12 lít dạng lò nướng); gợi ý câu 3 về lòng nồi chống dính còn nguyên, thanh nhiệt nóng nhanh.
                          * Ví dụ nếu là Bếp từ/bếp điện: gợi ý câu 4 về bếp đơn/đôi, công suất (Ví dụ: bếp đơn 2000W, bếp đôi âm 4000W, bếp hồng ngoại đơn); gợi ý câu 3 về mặt kính nguyên vẹn không nứt vỡ, quạt tản nhiệt êm.
                          * Tương tự với mọi loại sản phẩm '%s' khác: Hãy tự suy luận thông số cốt lõi và gợi ý sát thực tế nhất cho thiết bị đó.

                        ĐỊNH DẠNG ĐẦU RA BẮT BUỘC:
                        - Mỗi câu hỏi nằm trên một dòng riêng biệt theo đúng cú pháp:
                          **[Tên câu hỏi]?** (Ví dụ: gợi ý 1, gợi ý 2, gợi ý 3, gợi ý 4)
                        - Bắt buộc phải có từ 'Ví dụ:' trong ngoặc đơn, các gợi ý cách nhau bằng dấu phẩy.
                        - KHÔNG viết thêm bất kỳ lời chào, dẫn dắt hay kết luận nào. CHỈ trả về đúng 4 dòng câu hỏi.
                        """,
                        itemName, categoryName, itemName, itemName, itemName, itemName, itemName);

                String generatedTemplate = chatClient.prompt(promptText).call().content();

                if (template == null) {
                    template = new CategoryQuestionTemplate();
                    template.setCategoryId(request.getCategoryId());
                    template.setItemId(request.getItemId());
                }
                template.setTemplateText(generatedTemplate);
                template = templateRepository.save(template);
            }

            String templateText = template != null ? template.getTemplateText() : "";
            if (templateText != null && !templateText.isBlank()) {
                // Chuẩn hóa xuống dòng nếu các câu hỏi bị dính liền trên 1 dòng
                templateText = templateText.replaceAll("\\)\\s+(?=[A-ZÀ-Ỹ0-9\\*\\#])", ")\n");
                templateText = templateText.replaceAll("\\?\\s+(?=[A-ZÀ-Ỹ0-9\\*\\#])", "?\n");
            }

            // Fetch session to set on post
            com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(aiResponse.getSessionId())
                    .orElse(null);
            post.setAiChatSession(session);
            post.setTemplate(template);
            postRepository.save(post);

            return new PostInitResponse(
                    post.getId(),
                    aiResponse.getSessionId(),
                    templateText,
                    cleanDescription);
        } else {
            // Manual mode: No AI visual scan, no credit deducted
            post.setAiDescription(null);
            post.setDescription(null);
            post.setDescriptionAccepted(false);
            postRepository.save(post);

            return new PostInitResponse(
                    post.getId(),
                    null,
                    null,
                    null);
        }
    }

    @Override
    @Transactional
    public com.secondlife.secondlife.dto.response.PostFinalizeResponse finalizeChatAndDescription(UUID userId, UUID sessionId) {
        return finalizeChatAndDescription(userId, sessionId, null);
    }

    @Override
    @Transactional
    public com.secondlife.secondlife.dto.response.PostFinalizeResponse finalizeChatAndDescription(UUID userId,
                                                                                                  UUID sessionId,
                                                                                                  String message) {
        com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getUser() == null || !userId.equals(session.getUser().getId())) {
            throw new ForbiddenException("This chat session belongs to another user");
        }

        Post post = session.getPostId() == null ? null : listingAccess.owned(userId, session.getPostId(), true);
        if (post != null) listingAccess.requireDraft(post);

        // Deduct 1 AI_CHAT credit each time seller clicks finalize
        String idempotencyKey = "AI_FINALIZE:" + sessionId + ":" + UUID.randomUUID();
        listingCredits.consume(userId, com.secondlife.secondlife.enums.CreditType.AI_CHAT, idempotencyKey);

        if (message != null && !message.isBlank()) {
            com.secondlife.secondlife.entity.AiChatMessage userDbMsg = new com.secondlife.secondlife.entity.AiChatMessage();
            userDbMsg.setSession(session);
            userDbMsg.setRole("USER");
            userDbMsg.setMessageContent(message.trim());
            chatMessageRepository.save(userDbMsg);
            session.setMessageCount(session.getMessageCount() + 1);
            sessionRepository.save(session);
        }

        var finalizeResponse = aiChatService.finalizeChat(sessionId, userId);

        if (post != null) {
            post.setAiDescription(finalizeResponse.getDescription());
            post.setDescription(finalizeResponse.getDescription());
            post.setDescriptionAccepted(false);
            if (finalizeResponse.getTitle() != null && !finalizeResponse.getTitle().isBlank()) {
                post.setTitle(finalizeResponse.getTitle());
            }
            postRepository.save(post);
        }

        return finalizeResponse;
    }

    @Override
    @Transactional
    public com.secondlife.secondlife.dto.response.PostFinalizeResponse regenerateChatAndDescription(UUID userId, UUID sessionId) {
        return regenerateChatAndDescription(userId, sessionId, null);
    }

    @Override
    @Transactional
    public com.secondlife.secondlife.dto.response.PostFinalizeResponse regenerateChatAndDescription(UUID userId,
                                                                                                    UUID sessionId,
                                                                                                    String message) {
        com.secondlife.secondlife.entity.AiChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        if (session.getUser() == null || !userId.equals(session.getUser().getId())) {
            throw new ForbiddenException("This chat session belongs to another user");
        }

        Post post = session.getPostId() == null ? null : listingAccess.owned(userId, session.getPostId(), true);
        if (post != null) listingAccess.requireDraft(post);

        // Deduct 1 AI_CHAT credit each time seller clicks regenerate
        String idempotencyKey = "AI_REGENERATE:" + sessionId + ":" + UUID.randomUUID();
        listingCredits.consume(userId, com.secondlife.secondlife.enums.CreditType.AI_CHAT, idempotencyKey);

        if (message != null && !message.isBlank()) {
            com.secondlife.secondlife.entity.AiChatMessage userDbMsg = new com.secondlife.secondlife.entity.AiChatMessage();
            userDbMsg.setSession(session);
            userDbMsg.setRole("USER");
            userDbMsg.setMessageContent(message.trim());
            chatMessageRepository.save(userDbMsg);
            session.setMessageCount(session.getMessageCount() + 1);
            sessionRepository.save(session);
        }

        var finalizeResponse = aiChatService.regenerateDescription(sessionId, userId);
        if (post != null) {
            post.setAiDescription(finalizeResponse.getDescription());
            post.setDescription(finalizeResponse.getDescription());
            post.setDescriptionAccepted(false);
            if (finalizeResponse.getTitle() != null && !finalizeResponse.getTitle().isBlank()) {
                post.setTitle(finalizeResponse.getTitle());
            }
            postRepository.save(post);
        }

        return finalizeResponse;
    }

    @Override
    @Transactional
    public String suggestTitle(UUID userId, UUID postId, String context) {
        Post post = listingAccess.owned(userId, postId, true);
        listingAccess.requireDraft(post);

        // Deduct 1 AI_CHAT credit each time seller generates title with AI
        String idempotencyKey = "AI_TITLE:" + postId + ":" + UUID.randomUUID();
        listingCredits.consume(userId, CreditType.AI_CHAT, idempotencyKey);

        final String categoryName = post.getCategoryId() != null ?
                categoryRepository.findById(post.getCategoryId()).map(com.secondlife.secondlife.entity.Category::getName).orElse("Không xác định")
                : "Không xác định";
        final String itemName = post.getItemId() != null ?
                itemRepository.findById(post.getItemId()).map(com.secondlife.secondlife.entity.Item::getName).orElse("Không xác định")
                : "Không xác định";
        final String description = post.getDescription() != null && !post.getDescription().isBlank() ? post.getDescription() : post.getAiDescription();

        String promptText = String.format("""
                Bạn là chuyên gia marketing và tối ưu bài đăng thương mại điện tử chuyên đồ gia dụng cũ tại Việt Nam.
                Hãy tạo đúng 01 tiêu đề bài đăng bán hàng hấp dẫn, chuyên nghiệp và chuẩn SEO dựa trên thông tin sau:
                - Danh mục: %s
                - Loại sản phẩm: %s
                - Thông tin phụ / Tình trạng: %s
                - Mô tả tóm tắt: %s

                YÊU CẦU BẮT BUỘC:
                - Độ dài tiêu đề: từ 35 đến 80 ký tự.
                - Chứa tên thiết bị, thương hiệu (nếu có), phân khúc/tính năng nổi bật và độ mới thực tế (ví dụ: Like New 99%%, Đẹp 95%%, Chính Hãng...).
                - CHỈ trả về đúng 01 dòng tiêu đề duy nhất. TUYỆT ĐỐI KHÔNG có dấu ngoặc kép, không có lời chào hay giải thích gì thêm.
                """,
                categoryName, itemName, context != null ? context : "", description != null ? description : "");

        String generatedTitle = chatClient.prompt(promptText).call().content();
        if (generatedTitle != null && !generatedTitle.isBlank()) {
            generatedTitle = generatedTitle.replaceAll("^[\\\"'\\*\\-\\s]+|[\\\"'\\*\\-\\s]+$", "").trim();
            post.setTitle(generatedTitle);
            postRepository.save(post);
        } else {
            generatedTitle = post.getTitle();
        }
        return generatedTitle;
    }

    @Override
    @Transactional
    public PostSubmitResponse submitPost(UUID userId, UUID postId, com.secondlife.secondlife.dto.request.PostSubmitRequest request) {

        Post post = listingAccess.owned(userId, postId, true);

        if (request.getTitle() == null || request.getTitle().isBlank() || request.getTitle().length() > 255

                || request.getDescription() == null || request.getDescription().isBlank() || request.getDescription().length() > 10000

                || request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ONE) < 0

                || request.getPrice().scale() > 2 || request.getPrice().precision() - request.getPrice().scale() > 16)

            throw new BadRequestException("A valid title, description and positive VND price are required");

        // A retry after acceptance may return the prior outcome but cannot edit/re-up the listing.

        if (post.isListingCreditCharged() || "PENDING".equals(post.getStatus()) || "PENDING_INSPECTION".equals(post.getStatus())) {

            if (!("ACTIVE".equals(post.getStatus()) || "PENDING_INSPECTION".equals(post.getStatus()) || "PENDING".equals(post.getStatus()))

                    || !ListingFingerprint.normalize(request.getTitle()).equals(ListingFingerprint.normalize(post.getTitle()))

                    || !ListingFingerprint.normalize(request.getDescription()).equals(ListingFingerprint.normalize(post.getDescription()))

                    || request.getPrice().compareTo(post.getPrice()) != 0)

                throw new ConflictException("This post has already been submitted; re-up is not supported");

            return submitResponse(post);

        }

        listingAccess.requireDraft(post);

        listingAccess.requireVerifiedSeller(userId);

        if (!post.isDescriptionAccepted() || !ListingFingerprint.normalize(request.getDescription()).equals(ListingFingerprint.normalize(post.getDescription())))

            throw new ConflictException("Accept the latest product description before submitting");

        if (post.getImageUrl() == null || post.getImageUrl().isBlank()) throw new BadRequestException("A product image is required");

        publication.requirePublishAvailable(userId);

        post.setTitle(request.getTitle().trim());

        post.setDescription(request.getDescription().trim());

        post.setPrice(request.getPrice());

        post.setShippingWeight(request.getShippingWeight());

        post.setShippingLength(request.getShippingLength());

        post.setShippingWidth(request.getShippingWidth());

        post.setShippingHeight(request.getShippingHeight());

        submissionLock.lock();

        var matches = new java.util.ArrayList<UUID>();

        for (Post other : postRepository.findByIdNotAndStatusIn(postId,

                java.util.List.of("ACTIVE", "PENDING_INSPECTION", "PENDING"))) {

            boolean sameImage = !java.util.Collections.disjoint(post.getImageFingerprints(), other.getImageFingerprints());

            boolean visuallySimilar = post.getImages().stream().anyMatch(image -> other.getImages().stream().anyMatch(candidate ->

                    imageSimilarity.similar(image.getPerceptualFingerprint(), candidate.getPerceptualFingerprint())));

            boolean sameProduct = userId.equals(other.getUser().getId()) && java.util.Objects.equals(post.getCategoryId(), other.getCategoryId())

                    && java.util.Objects.equals(post.getItemId(), other.getItemId())

                    && ListingFingerprint.normalize(post.getTitle()).equals(ListingFingerprint.normalize(other.getTitle()))

                    && ListingFingerprint.normalize(post.getDescription()).equals(ListingFingerprint.normalize(other.getDescription()));

            if (sameImage || visuallySimilar || sameProduct) matches.add(other.getId());

        }

        post.setReviewReason(null);

        post.setDuplicatePostIds(null);

        post.setReviewedBy(null); post.setReviewedAt(null);

        if (!matches.isEmpty()) {

            post.setStatus("PENDING");

            post.setRejectionReason(null);

            post.setReviewReason("Possible duplicate product images or listing content; STAFF review required");

            post.setDuplicatePostIds(matches.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(",")));

            postRepository.save(post);

            return submitResponse(post);

        }

        String scanResult = aiScanPost(post);

        if (scanResult.startsWith("REJECTED:")) {

            String reason = scanResult.substring("REJECTED:".length()).trim();

            post.setStatus("REJECTED");

            post.setRejectionReason("AI tự động từ chối: " + reason);

            postRepository.save(post);

            return new PostSubmitResponse(

                    "REJECTED",

                    false,

                    null,

                    null,

                    "Bài đăng bị từ chối bởi hệ thống AI: " + reason,

                    null

            );

        }



        publication.accept(post);

        postRepository.save(post);

        return submitResponse(post);

    }



    private PostSubmitResponse submitResponse(Post post) {

        if ("PENDING".equals(post.getStatus()))

            return new PostSubmitResponse("PENDING", false, null, null, "Bài có dấu hiệu trùng lặp, đang chờ STAFF kiểm duyệt; chưa trừ credit đăng bài", null);

        boolean inspection = "PENDING_INSPECTION".equals(post.getStatus());

        return new PostSubmitResponse(post.getStatus(), inspection, inspection ? inspectionFee : null,

                inspection ? shippingFee : null, inspection

                ? "Đã nhận bài và tạo đơn kiểm định. Phí kiểm định/vận chuyển thanh toán qua luồng riêng."

                : "Bài đăng đã được duyệt và hiển thị trên sàn.", null);

    }



    /**

     * Gọi Google Gemini để scan bài đăng.

     * Returns "APPROVED" hoặc "REJECTED:<reason>"

     */

    private String aiScanPost(Post post) {

        String prompt = String.format("""

                Bạn là hệ thống kiểm duyệt tự động cho sàn mua bán đồ cũ SecondLife.

                Hãy đánh giá bài đăng sản phẩm sau có phù hợp để hiển thị trên sàn hay không:

                Tiêu đề: %s

                Mô tả: %s

                Giá: %s VNĐ



                TIÊU CHÍ KIỂM DUYỆT:

                1. HỢP LỆ (APPROVED):

                   - Sản phẩm là đồ dùng, gia dụng, thiết bị sinh hoạt cũ/đã qua sử dụng hợp pháp.

                   - Mô tả rõ ràng (chấp nhận định dạng Markdown hoặc mô tả do AI hỗ trợ tạo).

                   - Mức giá khả thi (không phải giá 0 VNĐ hoặc spam số ảo).

                2. TỪ CHỐI (REJECTED):

                   - Hàng cấm theo quy định (vũ khí, ma túy, chất kích thích, động vật quý hiếm, nội dung đồi trụy).

                   - Dấu hiệu lừa đảo trắng trợn, spam vô nghĩa, lôi kéo giao dịch ngoài sàn.

                   - Tiêu đề và mô tả hoàn toàn sai lệch hoặc không liên quan đến đồ dùng cần bán.



                QUY TẮC BẮT BUỘC:

                CHỈ trả về đúng một trong hai dạng sau trên một dòng duy nhất, không giải thích thêm:

                APPROVED

                hoặc

                REJECTED:<lý do ngắn gọn bằng tiếng Việt>

                """,

                post.getTitle(), post.getDescription(), post.getPrice()

        );

        Prompt aiPrompt = new Prompt(new UserMessage(prompt));

        ChatClient googleClient = ChatClient.builder(googleChatModel).build();

        String result;

        try { result = googleClient.prompt(aiPrompt).call().content(); }

        catch (Exception ex) { throw new AiProviderException("AI moderation unavailable; no listing credit was consumed", ex); }

        if (result == null) throw new AiProviderException("AI moderation returned no result");

        result = result.trim();

        if (!"APPROVED".equals(result) && !(result.startsWith("REJECTED:") && result.substring(9).trim().length() > 0))

            throw new AiProviderException("AI moderation returned an invalid result");

        return result;

    }



    @Override

    @Transactional

    public void approvePost(UUID postId) {

        Post post = postRepository.findById(postId)

                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (!"PENDING".equals(post.getStatus())) {

            throw new RuntimeException("Post must be in PENDING status to be approved");

        }



        listingAccess.requireVerifiedSeller(post.getUser().getId());

        post.setStatus("ACTIVE");

        postRepository.save(post);

        reviews.approve(postId, null);

    }



    @Override

    @Transactional

    public void rejectPost(UUID postId, String reason) {

        reviews.reject(postId, null, reason);

        Post post = postRepository.findById(postId)

                .orElseThrow(() -> new RuntimeException("Post not found"));



        if (!"PENDING".equals(post.getStatus())) {

            throw new RuntimeException("Post must be in PENDING status to be rejected");

        }



        post.setStatus("REJECTED");

        post.setRejectionReason(reason);

        postRepository.save(post);

    }



    @Override

    @Transactional(readOnly = true)

    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getAdminPosts(

            String status, UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable) {

        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> {

            if (Long.class != query.getResultType()) {

                jakarta.persistence.criteria.Fetch<Object, Object> userFetch = root.fetch("user", jakarta.persistence.criteria.JoinType.LEFT);

                userFetch.fetch("profile", jakarta.persistence.criteria.JoinType.LEFT);

            }

            return cb.conjunction();

        };

        if (status != null && !status.isEmpty()) {

            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));

        }

        if (categoryId != null) {

            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));

        }

        if (itemId != null) {

            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));

        }

        return postRepository.findAll(spec, pageable).map(com.secondlife.secondlife.dto.response.PostDto::fromEntity);

    }



    @Override

    @Transactional(readOnly = true)

    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getPublicPosts(UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable) {

        // Public posts should only return ACTIVE ones

        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> {

            if (Long.class != query.getResultType()) {

                jakarta.persistence.criteria.Fetch<Object, Object> userFetch = root.fetch("user", jakarta.persistence.criteria.JoinType.LEFT);

                userFetch.fetch("profile", jakarta.persistence.criteria.JoinType.LEFT);

            }

            return cb.equal(root.get("status"), "ACTIVE");

        };

        

        if (categoryId != null) {

            spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));

        }

        if (itemId != null) {

            spec = spec.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));

        }

        return postRepository.findAll(spec, pageable).map(com.secondlife.secondlife.dto.response.PostDto::fromEntity);

    }



    @Override

    @Transactional(readOnly = true)

    public com.secondlife.secondlife.dto.response.PostDto getPostDetail(UUID postId) {

        Post post = postRepository.findById(postId).orElseThrow(() -> new NotFoundException("Post not found"));

        return com.secondlife.secondlife.dto.response.PostDto.fromEntity(post);

    }



    @Override
    @Transactional
    public AiPriceEstimationResponse estimatePrice(UUID userId, UUID postId, String requestId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {
            throw new ForbiddenException("This post belongs to another user");
        }

        listingAccess.requireDraft(post);

        // Deduct 1 valuation credit (each valuation / re-valuation consumes 1 credit)
        String valuationKey = "VALUATION:" + postId + ":" + UUID.randomUUID();
        listingCredits.consume(userId, com.secondlife.secondlife.enums.CreditType.VALUATION, valuationKey);



        // Build context from post data

        String itemName = post.getItemId() != null ?

                itemRepository.findById(post.getItemId())

                        .map(com.secondlife.secondlife.entity.Item::getName).orElse("Không xác định")

                : "Không xác định";

        String description = post.getDescription() != null ? post.getDescription() : "Không có mô tả";

        String priceContext = post.getAiSuggestedPrice() != null

                ? "Giá mà người bán đã đề xuất: " + post.getAiSuggestedPrice() + " VNĐ."

                : "";



        StringBuilder chatContext = new StringBuilder();
        java.util.UUID targetSessionId = null;
        if (requestId != null && !requestId.trim().isEmpty()) {
            try {
                targetSessionId = java.util.UUID.fromString(requestId.trim());
            } catch (Exception ignored) {
            }
        }

        // Defensive fallback: Nếu targetSessionId null hoặc không có tin nhắn, tự động lấy aiChatSession gắn liền với Post
        if (targetSessionId == null && post.getAiChatSession() != null) {
            targetSessionId = post.getAiChatSession().getId();
        }

        if (targetSessionId != null) {
            java.util.List<com.secondlife.secondlife.entity.AiChatMessage> messages =
                    chatMessageRepository.findBySessionIdOrderBySentAtAsc(targetSessionId);

            // Nếu sessionId do client truyền lên không có tin nhắn nhưng post có aiChatSession, ưu tiên session của post
            if (messages.isEmpty() && post.getAiChatSession() != null && !targetSessionId.equals(post.getAiChatSession().getId())) {
                targetSessionId = post.getAiChatSession().getId();
                messages = chatMessageRepository.findBySessionIdOrderBySentAtAsc(targetSessionId);
            }

            for (com.secondlife.secondlife.entity.AiChatMessage msg : messages) {
                // Chỉ lấy các thông tin người dùng cung cấp thực tế, bỏ qua prompt kỹ thuật phân tích ảnh ban đầu
                if ("USER".equals(msg.getRole()) && msg.getMessageContent() != null && !msg.getMessageContent().isBlank()) {
                    String content = msg.getMessageContent().trim();
                    if (!content.startsWith("Danh mục người bán chọn:")) {
                        chatContext.append("- ").append(content).append("\n");
                    }
                }
            }
        }



        String pricePrompt = String.format(
                """
                Bạn là chuyên gia thẩm định và định giá đồ gia dụng cũ tại thị trường Việt Nam.
                Nhiệm vụ của bạn là thẩm định sản phẩm dựa trên thông tin thực tế của sản phẩm và người bán để đưa ra khoảng giá thanh lý hợp lý nhất bằng VNĐ.

                THÔNG TIN SẢN PHẨM:
                - Loại sản phẩm: %s
                - Mô tả hiện tại: %s
                %s
                - Chi tiết người bán cung cấp thêm:
                %s

                QUY TẮC ĐỊNH GIÁ THỰC TẾ:
                1. Ước lượng giá mua mới của sản phẩm trên thị trường Việt Nam làm mốc chuẩn. Bỏ qua giá mua mới nếu người bán cung cấp mức giá phi thực tế.
                2. ĐÁNH GIÁ THEO HAO MÒN THỰC TẾ & TẦN SUẤT SỬ DỤNG (Ưu tiên ngoại quan, số lần dùng và tình trạng bảo hành/phụ kiện hơn là chỉ đếm thời gian mua):
                   - Hàng lướt / Like New: Mới dùng vài lần, nguyên hộp/phụ kiện, đẹp 98-99%% (kể cả đã mua vài tháng đến 1 năm nhưng ít dùng): Giảm 15-25%% so với giá mới.
                   - Hàng còn rất tốt / Dùng giữ gìn: Ngoại hình đẹp 90-95%%, không lỗi lầm, dùng ít hoặc mua 6 tháng - 1 năm tần suất sử dụng thấp: Giảm 25-35%% so với giá mới.
                   - Hàng sử dụng thông thường: Dùng đều đặn 1-2 năm, có trầy xước nhẹ/hao mòn tự nhiên, hết bảo hành: Giảm 35-50%% so với giá mới.
                   - Hàng đã dùng lâu / Đời cũ: Dùng trên 2-3 năm, có hao mòn rõ rệt hoặc lỗi thời: Giảm 50-65%% so với giá mới.
                3. Giá đề xuất (suggestedPrice) là giá thanh lý thực tế, dễ bán nhất trên thị trường đồ cũ Việt Nam. Tuyệt đối không định giá đồ cũ cao hơn hoặc bằng giá mới.
                4. Ràng buộc: fairPriceMin <= suggestedPrice <= fairPriceMax. Tất cả các giá đều là số nguyên dương VND (> 0).
                5. BẮT BUỘC chỉ trả về duy nhất định dạng JSON sau, không bọc markdown và không giải thích gì thêm:
                {"fairPriceMin": <số nguyên VND>, "fairPriceMax": <số nguyên VND>, "suggestedPrice": <số nguyên VND>, "expectedSellTime": "<ví dụ: 3-5 ngày>"}
                """,
                itemName, description, priceContext, chatContext.length() > 0 ? chatContext.toString() : "Không có thêm thông tin");
        ChatClient googleClient = ChatClient.builder(googleChatModel).build();

        String aiResult = googleClient.prompt(pricePrompt).call().content();



        BigDecimal fairPriceMin = BigDecimal.ZERO;

        BigDecimal fairPriceMax = BigDecimal.ZERO;

        BigDecimal suggestedPrice = BigDecimal.ZERO;

        String expectedSellTime = "3-5 ngày";



        try {

            // Extract JSON from the AI response

            String jsonStr = aiResult;

            if (aiResult.contains("{") && aiResult.contains("}")) {

                jsonStr = aiResult.substring(aiResult.indexOf("{"), aiResult.lastIndexOf("}") + 1);

            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(jsonStr);

            fairPriceMin = BigDecimal.valueOf(node.get("fairPriceMin").asLong());

            fairPriceMax = BigDecimal.valueOf(node.get("fairPriceMax").asLong());

            suggestedPrice = BigDecimal.valueOf(node.get("suggestedPrice").asLong());

            if (node.has("expectedSellTime")) {

                expectedSellTime = node.get("expectedSellTime").asText();

            }

        } catch (Exception e) {

            // Fallback: try to extract numbers from raw text

            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[0-9]{5,}").matcher(aiResult.replaceAll("[.,]", ""));

            java.util.List<Long> nums = new java.util.ArrayList<>();

            while (m.find()) nums.add(Long.parseLong(m.group()));

            if (nums.size() >= 3) {

                fairPriceMin = BigDecimal.valueOf(nums.get(0));

                fairPriceMax = BigDecimal.valueOf(nums.get(1));

                suggestedPrice = BigDecimal.valueOf(nums.get(2));

            }

        }



        if (suggestedPrice != null && suggestedPrice.compareTo(BigDecimal.ZERO) > 0) {
            post.setAiSuggestedPrice(suggestedPrice);
            postRepository.save(post);
        }

        return AiPriceEstimationResponse.builder()
                .requestId(requestId)
                .fairPriceMin(fairPriceMin)
                .fairPriceMax(fairPriceMax)
                .suggestedPrice(suggestedPrice)
                .modelVersion("SecondLife-AI-v2.1")
                .expectedSellTime(expectedSellTime)
                .createdAt(java.time.Instant.now())
                .build();
    }



    @Override

    @Transactional(readOnly = true)

    public org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto> getMyPosts(UUID userId,

                                                                                                           org.springframework.data.domain.Pageable pageable) {

        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, cb) -> {

            if (Long.class != query.getResultType()) {

                jakarta.persistence.criteria.Fetch<Object, Object> userFetch = root.fetch("user", jakarta.persistence.criteria.JoinType.LEFT);

                userFetch.fetch("profile", jakarta.persistence.criteria.JoinType.LEFT);

            }

            return cb.equal(root.get("user").get("id"), userId);

        };

        return postRepository.findAll(spec, pageable).map(com.secondlife.secondlife.dto.response.PostDto::fromEntity);

    }



    @Override

    @Transactional

    public com.secondlife.secondlife.dto.response.PostDto updateDraft(UUID userId, UUID postId, com.secondlife.secondlife.dto.request.UpdateDraftRequest request) {

        Post post = postRepository.findById(postId)

                .orElseThrow(() -> new NotFoundException("Post not found"));

        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {

            throw new ForbiddenException("This post belongs to another user");

        }



        post.setTitle(request.getTitle());

        post.setDescription(request.getDescription());

        post.setItemCondition(request.getItemCondition());

        post.setPrice(request.getPrice());

        

        if (request.getShippingWeight() != null) post.setShippingWeight(request.getShippingWeight());

        if (request.getShippingLength() != null) post.setShippingLength(request.getShippingLength());

        if (request.getShippingWidth() != null) post.setShippingWidth(request.getShippingWidth());

        if (request.getShippingHeight() != null) post.setShippingHeight(request.getShippingHeight());



        // Status may remain DRAFT or whatever it is, unless we explicitly change it.

        // Frontend sends "status": 'DRAFT', let's assume it should stay DRAFT if it was DRAFT

        post = postRepository.save(post);

        return com.secondlife.secondlife.dto.response.PostDto.fromEntity(post);

    }



    @Override

    @Transactional

    public com.secondlife.secondlife.dto.response.PostDto acceptDescription(UUID userId, UUID postId, com.secondlife.secondlife.dto.request.AcceptDescriptionRequest request) {

        Post post = postRepository.findById(postId)

                .orElseThrow(() -> new NotFoundException("Post not found"));

        if (post.getUser() == null || !userId.equals(post.getUser().getId())) {

            throw new ForbiddenException("This post belongs to another user");

        }



        post.setDescription(request.getDescription());

        // For 'descriptionAccepted' behavior, we save it to description.

        // If there is an 'aiDescription' we might want to keep it unchanged.

        post = postRepository.save(post);

        return com.secondlife.secondlife.dto.response.PostDto.fromEntity(post);

    }

}


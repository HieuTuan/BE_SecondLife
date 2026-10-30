package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.validation.ImageUploadValidator;
import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.InvalidRequestFieldException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.PostMapper;
import com.secondlife.secondlife.repository.AiChatMessageRepository;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.ListingFingerprintImpl;
import com.secondlife.secondlife.service.impl.ListingImageSimilarityImpl;
import com.secondlife.secondlife.service.impl.PostServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListingImageValidationTest {
    private final UUID ownerId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UserRepository users = mock(UserRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final CloudinaryService cloud = mock(CloudinaryService.class);
    private final ListingCreditService credits = mock(ListingCreditService.class);
    private PostServiceImpl service;

    @BeforeEach
    void setup() throws Exception {
        when(users.findById(ownerId)).thenReturn(Optional.of(new User()));
        when(categories.existsById(categoryId)).thenReturn(true);
        service = new PostServiceImpl(posts, users, credits, mock(ListingAccessService.class),
                mock(AiChatService.class), mock(CategoryQuestionTemplateRepository.class),
                mock(AiChatSessionRepository.class), mock(AiChatMessageRepository.class), categories,
                mock(ItemRepository.class), mock(AiChatGateway.class), cloud,
                mock(InspectionOrderRepository.class), mock(AiChatGateway.class),
                mock(ListingPublicationService.class), mock(ListingReviewService.class),
                new ListingImageSimilarityImpl(16000, 25000000, 20971520, 100, 8, 12, 90),
                new ListingFingerprintImpl(), BigDecimal.ZERO, BigDecimal.ZERO, 30,
                10485760, 3, 6, mock(PostMapper.class), null,
                new ImageUploadValidator(600, 16000, 25000000, 2560));
        doThrow(new AssertionError("Invalid batch must be rejected before any upload"))
                .when(cloud).uploadImage(any());
    }

    @Test
    void corruptSecondImageIsRejectedBeforeUploadingTheFirstImage() throws Exception {
        var corrupt = new MockMultipartFile("images", "broken.jpg", "image/jpeg", "not-an-image".getBytes());
        assertThrows(InvalidRequestFieldException.class,
                () -> service.initPost(ownerId, request(List.of(photo(800, 600, Color.RED), corrupt,
                        photo(800, 600, Color.BLUE)))));
        verifyNoInteractions(cloud, credits);
    }

    @Test
    void shortSideBelow600IsRejectedBeforeUpload() throws Exception {
        assertThrows(InvalidRequestFieldException.class,
                () -> service.initPost(ownerId, request(List.of(photo(800, 599, Color.RED),
                        photo(800, 600, Color.GREEN), photo(800, 600, Color.BLUE)))));
        verifyNoInteractions(cloud, credits);
    }

    @Test
    void identicalPhotosDoNotSatisfyTheThreePhotoRequirement() throws Exception {
        var repeated = photo(800, 600, Color.RED);
        assertThrows(InvalidRequestFieldException.class,
                () -> service.initPost(ownerId, request(List.of(repeated, repeated, photo(800, 600, Color.BLUE)))));
        verifyNoInteractions(cloud, credits);
    }

    @Test
    void partialUploadFailureCleansCreatedAssetsWithoutSavingOrCharging() throws Exception {
        reset(cloud);
        var first = new CloudinaryService.UploadedImage("https://example.test/first.jpg", "listings/images/first");
        IOException original = new IOException("Storage unavailable");
        when(cloud.uploadImage(any())).thenReturn(first).thenThrow(original);
        // Cleanup failure must not replace the storage failure returned to the caller.
        doThrow(new IOException("Cleanup unavailable")).when(cloud).deleteImage(first.publicId());
        var failure = assertThrows(IllegalStateException.class, () -> service.initPost(ownerId, request(validPhotos())));
        assertSame(original, failure.getCause());
        verify(cloud).deleteImage(first.publicId());
        verify(posts, never()).save(any());
        verifyNoInteractions(credits);
    }

    @Test
    void draftSaveFailureCleansEveryNewAsset() throws Exception {
        reset(cloud);
        var uploaded = List.of(new CloudinaryService.UploadedImage("https://example.test/1.jpg", "listings/images/1"),
                new CloudinaryService.UploadedImage("https://example.test/2.jpg", "listings/images/2"),
                new CloudinaryService.UploadedImage("https://example.test/3.jpg", "listings/images/3"));
        when(cloud.uploadImage(any())).thenReturn(uploaded.get(0), uploaded.get(1), uploaded.get(2));
        var databaseFailure = new IllegalStateException("Database unavailable");
        when(posts.save(any())).thenThrow(databaseFailure);
        assertSame(databaseFailure, assertThrows(IllegalStateException.class,
                () -> service.initPost(ownerId, request(validPhotos()))));
        for (var asset : uploaded) verify(cloud).deleteImage(asset.publicId());
        verifyNoInteractions(credits);
    }

    @Test
    void validManualListingSavesInOrderAndDoesNotConsumeAiCredit() throws Exception {
        reset(cloud);
        when(cloud.uploadImage(any())).thenReturn(new CloudinaryService.UploadedImage("https://example.test/1.jpg", "listings/images/1"),
                new CloudinaryService.UploadedImage("https://example.test/2.jpg", "listings/images/2"),
                new CloudinaryService.UploadedImage("https://example.test/3.jpg", "listings/images/3"));
        var category = new Category();
        category.setName("Tủ lạnh");
        when(categories.findById(categoryId)).thenReturn(Optional.of(category));
        when(posts.save(any())).thenAnswer(call -> {
            Post post = call.getArgument(0);
            if (post.getId() == null) post.setId(UUID.randomUUID());
            assertEquals(List.of("https://example.test/1.jpg", "https://example.test/2.jpg", "https://example.test/3.jpg"),
                    post.getImages().stream().map(image -> image.getImageUrl()).toList());
            assertEquals("DRAFT", post.getStatus());
            return post;
        });
        var response = service.initPost(ownerId, request(validPhotos()));
        assertEquals(null, response.sessionId());
        verify(posts, times(2)).save(any());
        verify(cloud, never()).deleteImage(any());
        verifyNoInteractions(credits);
    }

    private List<MultipartFile> validPhotos() throws Exception {
        return List.of(photo(800, 600, Color.RED), photo(800, 600, Color.GREEN), photo(800, 600, Color.BLUE));
    }

    private PostInitRequest request(List<MultipartFile> photos) {
        return new PostInitRequest(categoryId, null, null, null, photos, "manual");
    }

    static MockMultipartFile photo(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return new MockMultipartFile("images", "photo.jpg", "image/jpeg", out.toByteArray());
    }
}

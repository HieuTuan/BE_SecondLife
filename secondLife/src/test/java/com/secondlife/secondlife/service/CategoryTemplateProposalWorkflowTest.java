package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ApproveTemplateProposalRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.RejectTemplateProposalRequest;
import com.secondlife.secondlife.dto.request.UpdateTemplateProposalRequest;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateProposalResponse;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateResponse;
import com.secondlife.secondlife.dto.response.ResolvedQuestionTemplateResponse;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.CategoryQuestionTemplateProposal;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.TemplateProposalStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.CategoryQuestionTemplateMapper;
import com.secondlife.secondlife.mapper.CategoryQuestionTemplateProposalMapper;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateProposalRepository;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.CatalogManagementServiceImpl;
import com.secondlife.secondlife.service.impl.CategoryTemplateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CategoryTemplateProposalWorkflowTest {

    private CategoryRepository categoryRepository;
    private ItemRepository itemRepository;
    private CategoryQuestionTemplateRepository templateRepository;
    private CategoryQuestionTemplateProposalRepository proposalRepository;
    private PostRepository postRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private AiChatGateway googleChatGateway;
    private AiChatGateway ollamaChatGateway;
    private CatalogManagementServiceImpl catalogManagementService;
    private CategoryTemplateServiceImpl templateService;

    private UUID categoryId;
    private UUID tvItemId;
    private UUID sellerId;
    private UUID adminId;
    private Category electronicsCategory;
    private Item tvItem;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        itemRepository = mock(ItemRepository.class);
        templateRepository = mock(CategoryQuestionTemplateRepository.class);
        proposalRepository = mock(CategoryQuestionTemplateProposalRepository.class);
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        googleChatGateway = mock(AiChatGateway.class);
        ollamaChatGateway = mock(AiChatGateway.class);

        catalogManagementService = new CatalogManagementServiceImpl(
                mock(CatalogService.class),
                categoryRepository,
                itemRepository,
                templateRepository,
                postRepository
        );

        CategoryQuestionTemplateMapper templateMapper = new CategoryQuestionTemplateMapper(
                Mappers.getMapper(CategoryQuestionTemplateMapper.Delegate.class)
        );
        CategoryQuestionTemplateProposalMapper proposalMapper = new CategoryQuestionTemplateProposalMapper(
                Mappers.getMapper(CategoryQuestionTemplateProposalMapper.Delegate.class)
        );

        templateService = new CategoryTemplateServiceImpl(
                catalogManagementService,
                categoryRepository,
                itemRepository,
                templateRepository,
                proposalRepository,
                templateMapper,
                proposalMapper,
                userRepository,
                notificationService,
                googleChatGateway,
                ollamaChatGateway,
                "gemma4:31b-cloud",
                null
        );

        categoryId = UUID.randomUUID();
        tvItemId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        electronicsCategory = new Category();
        electronicsCategory.setId(categoryId);
        electronicsCategory.setName("Điện tử - Điện lạnh");

        tvItem = new Item();
        tvItem.setId(tvItemId);
        tvItem.setCategory(electronicsCategory);
        tvItem.setName("Smart Tivi");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(electronicsCategory));
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(itemRepository.findById(tvItemId)).thenReturn(Optional.of(tvItem));
        when(itemRepository.existsById(tvItemId)).thenReturn(true);
    }

    @Test
    void returnsOfficialTemplateWithoutOverwritingAdminTextOrCallingAi() {
        UUID officialId = UUID.randomUUID();
        CategoryQuestionTemplate official = new CategoryQuestionTemplate();
        official.setId(officialId);
        official.setCategoryId(categoryId);
        official.setItemId(tvItemId);
        // Single-line custom template authored by Admin should never be overwritten
        official.setTemplateText("**Tivi của bạn bao nhiêu inch?** (Ví dụ: 43 inch, 55 inch, 65 inch)");

        when(templateRepository.findByCategoryIdAndItemId(categoryId, tvItemId))
                .thenReturn(Optional.of(official));

        ResolvedQuestionTemplateResponse resolved = templateService.resolveOrGenerateForListing(
                categoryId, tvItemId, sellerId
        );

        assertFalse(resolved.temporaryGenerated());
        assertEquals(officialId, resolved.officialTemplateId());
        assertEquals(
                "**Tivi của bạn bao nhiêu inch?** (Ví dụ: 43 inch, 55 inch, 65 inch)",
                resolved.templateText()
        );
        verifyNoInteractions(googleChatGateway, ollamaChatGateway, proposalRepository, notificationService);
        verify(templateRepository, never()).save(any());
        verify(templateRepository, never()).saveAndFlush(any());
    }

    @Test
    void generatesTemporarySpecializedTemplateCreatesPendingProposalAndNotifiesAdminsWithoutSavingToOfficialTable() {
        when(templateRepository.findByCategoryIdAndItemId(categoryId, tvItemId)).thenReturn(Optional.empty());
        when(templateRepository.findByCategoryIdAndItemIdIsNull(categoryId)).thenReturn(Optional.empty());
        when(proposalRepository.findByCategoryIdAndItemIdAndStatus(categoryId, tvItemId, TemplateProposalStatus.PENDING))
                .thenReturn(Optional.empty());

        String aiGenerated = String.join("\n",
                "**Tần suất sử dụng thực tế?** (Ví dụ: Mới dùng vài lần, Dùng ít, Dùng hằng ngày)",
                "**Hiện trạng ngoại quan và remote/chân đế đi kèm?** (Ví dụ: Đẹp 99% đủ remote giọng nói + chân đế, Xước nhẹ đủ điều khiển)",
                "**Tình trạng vận hành và bảo hành?** (Ví dụ: Nguyên zin còn bảo hành hãng, Chạy tốt hết bảo hành)",
                "**Kích thước màn hình Tivi là bao nhiêu inch?** (Ví dụ: 43 inch, 50 inch, 55 inch, 65 inch)",
                "**Độ phân giải và công nghệ Smart TV?** (Ví dụ: Smart TV 4K UHD, QLED 4K, OLED)"
        );
        when(googleChatGateway.complete(anyString())).thenReturn(aiGenerated);

        UUID savedProposalId = UUID.randomUUID();
        when(proposalRepository.saveAndFlush(any(CategoryQuestionTemplateProposal.class)))
                .thenAnswer(inv -> {
                    CategoryQuestionTemplateProposal p = inv.getArgument(0);
                    p.setId(savedProposalId);
                    return p;
                });
        when(userRepository.findActiveCatalogAdminIds()).thenReturn(List.of(adminId));

        ResolvedQuestionTemplateResponse resolved = templateService.resolveOrGenerateForListing(
                categoryId, tvItemId, sellerId
        );

        assertTrue(resolved.temporaryGenerated());
        assertNull(resolved.officialTemplateId());
        assertTrue(resolved.templateText().contains("bao nhiêu inch"));
        assertTrue(resolved.templateText().contains("4K UHD"));

        // Verify prompt instructed AI to keep core condition questions + add specialized specs (TV inch, etc.)
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(googleChatGateway).complete(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().contains("Smart Tivi"));
        assertTrue(promptCaptor.getValue().contains("bao nhiêu inch"));

        // Official DB table MUST NOT be written before Admin approves
        verify(templateRepository, never()).save(any());
        verify(templateRepository, never()).saveAndFlush(any());

        // Proposal must be saved as PENDING with requestCount = 1
        ArgumentCaptor<CategoryQuestionTemplateProposal> proposalCaptor =
                ArgumentCaptor.forClass(CategoryQuestionTemplateProposal.class);
        verify(proposalRepository).saveAndFlush(proposalCaptor.capture());
        assertEquals(TemplateProposalStatus.PENDING, proposalCaptor.getValue().getStatus());
        assertEquals(1, proposalCaptor.getValue().getRequestCount());
        assertEquals(sellerId, proposalCaptor.getValue().getTriggeredBy());

        // Admin receives In-App notification
        verify(notificationService).notifyInApp(
                eq(adminId),
                anyString(),
                anyString(),
                eq(NotificationType.SYSTEM),
                eq(savedProposalId),
                eq("TEMPLATE_PROPOSAL"),
                eq("/admin?subtab=catalog-ai")
        );
    }

    @Test
    void reusesExistingPendingProposalAndIncrementsRequestCountWithoutExtraAiCalls() {
        CategoryQuestionTemplateProposal pending = new CategoryQuestionTemplateProposal();
        pending.setId(UUID.randomUUID());
        pending.setCategoryId(categoryId);
        pending.setItemId(tvItemId);
        pending.setStatus(TemplateProposalStatus.PENDING);
        pending.setRequestCount(2);
        pending.setTemplateText("**Tivi bao nhiêu inch?** (Ví dụ: 43 inch, 55 inch, 65 inch)");

        when(templateRepository.findByCategoryIdAndItemId(categoryId, tvItemId)).thenReturn(Optional.empty());
        when(templateRepository.findByCategoryIdAndItemIdIsNull(categoryId)).thenReturn(Optional.empty());
        when(proposalRepository.findByCategoryIdAndItemIdAndStatus(categoryId, tvItemId, TemplateProposalStatus.PENDING))
                .thenReturn(Optional.of(pending));
        when(proposalRepository.saveAndFlush(any(CategoryQuestionTemplateProposal.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ResolvedQuestionTemplateResponse resolved = templateService.resolveOrGenerateForListing(
                categoryId, tvItemId, sellerId
        );

        assertTrue(resolved.temporaryGenerated());
        assertNull(resolved.officialTemplateId());
        assertEquals(3, pending.getRequestCount());
        verifyNoInteractions(googleChatGateway, ollamaChatGateway, notificationService);
    }

    @Test
    void adminCanManuallyEditAndApproveProposalPersistingEditedTemplateToOfficialDb() {
        UUID proposalId = UUID.randomUUID();
        CategoryQuestionTemplateProposal pending = new CategoryQuestionTemplateProposal();
        pending.setId(proposalId);
        pending.setCategoryId(categoryId);
        pending.setItemId(tvItemId);
        pending.setStatus(TemplateProposalStatus.PENDING);
        pending.setRequestCount(3);
        pending.setCreatedAt(Instant.now());
        pending.setUpdatedAt(Instant.now());
        pending.setTemplateText("**Câu hỏi gốc AI?** (Ví dụ: A, B)");

        when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(pending));
        when(proposalRepository.saveAndFlush(any(CategoryQuestionTemplateProposal.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(templateRepository.findByCategoryIdAndItemId(categoryId, tvItemId))
                .thenReturn(Optional.empty());

        UUID createdOfficialId = UUID.randomUUID();
        when(templateRepository.saveAndFlush(any(CategoryQuestionTemplate.class)))
                .thenAnswer(inv -> {
                    CategoryQuestionTemplate t = inv.getArgument(0);
                    t.setId(createdOfficialId);
                    return t;
                });

        // 1. Admin saves intermediate manual edit while still PENDING
        String draftEdited = String.join("\n",
                "**Tần suất sử dụng Tivi?** (Ví dụ: Dùng ít cuối tuần, Dùng hằng ngày)",
                "**Kích thước màn hình Tivi bao nhiêu inch?** (Ví dụ: 43 inch, 55 inch, 65 inch, 75 inch)"
        );
        CategoryQuestionTemplateProposalResponse updatedResp = templateService.updateProposal(
                proposalId,
                new UpdateTemplateProposalRequest(draftEdited)
        );
        assertEquals(TemplateProposalStatus.PENDING, updatedResp.status());
        assertEquals(draftEdited, updatedResp.templateText());
        verify(templateRepository, never()).saveAndFlush(any());

        // 2. Admin tweaks further and approves to official DB
        String finalApprovedText = draftEdited + "\n**Tần số quét màn hình?** (Ví dụ: 60Hz, 120Hz chơi game)";
        CategoryQuestionTemplateResponse officialResp = templateService.approveProposal(
                proposalId,
                adminId,
                new ApproveTemplateProposalRequest(finalApprovedText)
        );

        assertEquals(createdOfficialId, officialResp.id());
        assertEquals(categoryId, officialResp.categoryId());
        assertEquals(tvItemId, officialResp.itemId());
        assertEquals(finalApprovedText, officialResp.templateText());

        assertEquals(TemplateProposalStatus.APPROVED, pending.getStatus());
        assertEquals(adminId, pending.getReviewedBy());
        assertNotNull(pending.getReviewedAt());
    }

    @Test
    void adminRejectionMarksProposalRejectedWithoutWritingOfficialTemplate() {
        UUID proposalId = UUID.randomUUID();
        CategoryQuestionTemplateProposal pending = new CategoryQuestionTemplateProposal();
        pending.setId(proposalId);
        pending.setCategoryId(categoryId);
        pending.setItemId(tvItemId);
        pending.setStatus(TemplateProposalStatus.PENDING);
        pending.setRequestCount(1);
        pending.setTemplateText("**Câu hỏi lỗi?** (Ví dụ: A, B)");

        when(proposalRepository.findById(proposalId)).thenReturn(Optional.of(pending));
        when(proposalRepository.saveAndFlush(any(CategoryQuestionTemplateProposal.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CategoryQuestionTemplateProposalResponse rejected = templateService.rejectProposal(
                proposalId,
                adminId,
                new RejectTemplateProposalRequest("Chưa sát thông số thực tế")
        );

        assertEquals(TemplateProposalStatus.REJECTED, rejected.status());
        assertEquals("Chưa sát thông số thực tế", rejected.rejectionReason());
        assertEquals(adminId, rejected.reviewedBy());
        verify(templateRepository, never()).save(any());
        verify(templateRepository, never()).saveAndFlush(any());
    }

    @Test
    void fallsBackToOllamaWhenGoogleGeminiFailsAndStillCreatesPendingProposal() {
        when(templateRepository.findByCategoryIdAndItemId(categoryId, tvItemId)).thenReturn(Optional.empty());
        when(templateRepository.findByCategoryIdAndItemIdIsNull(categoryId)).thenReturn(Optional.empty());
        when(proposalRepository.findByCategoryIdAndItemIdAndStatus(categoryId, tvItemId, TemplateProposalStatus.PENDING))
                .thenReturn(Optional.empty());

        when(googleChatGateway.complete(anyString())).thenThrow(new RuntimeException("Gemini quota exceeded"));
        when(ollamaChatGateway.complete(any(Prompt.class)))
                .thenReturn("**Kích thước màn hình Tivi bao nhiêu inch?** (Ví dụ: 43 inch, 55 inch, 65 inch)");
        when(proposalRepository.saveAndFlush(any(CategoryQuestionTemplateProposal.class)))
                .thenAnswer(inv -> {
                    CategoryQuestionTemplateProposal p = inv.getArgument(0);
                    p.setId(UUID.randomUUID());
                    return p;
                });
        when(userRepository.findActiveCatalogAdminIds()).thenReturn(List.of());

        ResolvedQuestionTemplateResponse resolved = templateService.resolveOrGenerateForListing(
                categoryId, tvItemId, sellerId
        );

        assertTrue(resolved.temporaryGenerated());
        assertTrue(resolved.templateText().contains("bao nhiêu inch"));
        verify(ollamaChatGateway, times(1)).complete(any(Prompt.class));
        verify(proposalRepository, times(1)).saveAndFlush(any(CategoryQuestionTemplateProposal.class));
    }

    @Test
    void returnsRichSpecializedDeterministicFallbackWithoutThrowingWhenAllAiGatewaysFail() {
        when(templateRepository.findByCategoryIdAndItemId(categoryId, tvItemId)).thenReturn(Optional.empty());
        when(templateRepository.findByCategoryIdAndItemIdIsNull(categoryId)).thenReturn(Optional.empty());
        when(proposalRepository.findByCategoryIdAndItemIdAndStatus(categoryId, tvItemId, TemplateProposalStatus.PENDING))
                .thenReturn(Optional.empty());

        when(googleChatGateway.complete(anyString())).thenThrow(new RuntimeException("Gemini down"));
        when(ollamaChatGateway.complete(any(Prompt.class))).thenThrow(new RuntimeException("Ollama down"));

        ResolvedQuestionTemplateResponse resolved = templateService.resolveOrGenerateForListing(
                categoryId, tvItemId, sellerId
        );

        assertTrue(resolved.temporaryGenerated());
        assertNull(resolved.officialTemplateId());
        List<String> lines = resolved.templateText().lines().toList();
        assertEquals(6, lines.size());
        assertTrue(resolved.templateText().contains("Tần suất và mức độ sử dụng"));
        assertTrue(resolved.templateText().contains("bao nhiêu inch"));
        assertTrue(resolved.templateText().contains("Smart TV"));
        assertTrue(resolved.templateText().contains("tấm nền hiển thị"));
        // Never pollute Admin queue with static fallback when AI is offline
        verify(proposalRepository, never()).saveAndFlush(any());
    }

    @Test
    void catalogManagementSupportsCategoryWideTemplatesPreventsDuplicatesAndClearsPostReferencesOnDelete() {
        when(templateRepository.findByCategoryIdAndItemIdIsNull(categoryId)).thenReturn(Optional.empty());
        when(templateRepository.saveAndFlush(any(CategoryQuestionTemplate.class)))
                .thenAnswer(inv -> {
                    CategoryQuestionTemplate t = inv.getArgument(0);
                    t.setId(UUID.randomUUID());
                    return t;
                });

        // Category-level template (itemId = null) succeeds
        CategoryQuestionTemplate createdCatTemplate = catalogManagementService.createTemplate(
                new CategoryQuestionTemplateRequest(categoryId, null, "**Câu hỏi chung?** (Ví dụ: Tốt, Khá)")
        );
        assertNull(createdCatTemplate.getItemId());

        // Duplicate category-level template throws 409 ConflictException
        when(templateRepository.findByCategoryIdAndItemIdIsNull(categoryId))
                .thenReturn(Optional.of(createdCatTemplate));
        assertThrows(
                ConflictException.class,
                () -> catalogManagementService.createTemplate(
                        new CategoryQuestionTemplateRequest(categoryId, null, "**Trùng?** (Ví dụ: A, B)")
                )
        );

        // Item belonging to another category throws 400 BadRequestException
        UUID wrongCatId = UUID.randomUUID();
        Category wrongCategory = new Category();
        wrongCategory.setId(wrongCatId);
        Item mismatchedItem = new Item();
        mismatchedItem.setId(UUID.randomUUID());
        mismatchedItem.setCategory(wrongCategory);
        when(itemRepository.findById(mismatchedItem.getId())).thenReturn(Optional.of(mismatchedItem));
        assertThrows(
                BadRequestException.class,
                () -> catalogManagementService.createTemplate(
                        new CategoryQuestionTemplateRequest(categoryId, mismatchedItem.getId(), "**Sai danh mục?** (Ví dụ: A)")
                )
        );

        // Deleting official template detaches posts.template_id first
        when(templateRepository.existsById(createdCatTemplate.getId())).thenReturn(true);
        catalogManagementService.deleteTemplate(createdCatTemplate.getId());
        verify(postRepository).clearTemplateReference(createdCatTemplate.getId());
        verify(templateRepository).deleteById(createdCatTemplate.getId());
    }
}

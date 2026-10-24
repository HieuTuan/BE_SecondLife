package com.secondlife.secondlife.dto;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionChecklistItemRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.request.OrderRequestDTO;
import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.dto.response.InspectionSealVerificationResponse;
import com.secondlife.secondlife.dto.response.NotificationResponseDTO;
import com.secondlife.secondlife.enums.OrderPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DtoRecordCompatibilityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final com.fasterxml.jackson.databind.ObjectMapper legacyMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void defaultsAndExplicitNullsArePreservedInBothJacksonVersions() throws Exception {
        assertDefaults(mapper::readValue);
        assertDefaults(legacyMapper::readValue);
        assertFalse(legacyMapper.readValue("{\"mandatory\":null}", InspectionChecklistItemRequest.class).mandatory());
        assertThrows(tools.jackson.databind.exc.MismatchedInputException.class,
                () -> mapper.readValue("{\"mandatory\":null}", InspectionChecklistItemRequest.class));
    }

    private void assertDefaults(Reader reader) throws Exception {
        assertEquals(OrderPaymentMethod.WALLET, reader.read("{}", OrderRequestDTO.class).paymentMethod());
        assertNull(reader.read("{\"paymentMethod\":null}", OrderRequestDTO.class).paymentMethod());
        assertEquals(OrderPaymentMethod.BANK_TRANSFER,
                reader.read("{\"paymentMethod\":\"BANK_TRANSFER\"}", OrderRequestDTO.class).paymentMethod());
        var defaults = reader.read("{\"itemTitle\":\"Screen\"}", InspectionChecklistItemRequest.class);
        assertTrue(defaults.mandatory());
        assertEquals(0, defaults.orderIndex());
        var explicit = reader.read("{\"mandatory\":false,\"orderIndex\":null}", InspectionChecklistItemRequest.class);
        assertFalse(explicit.mandatory());
        assertNull(explicit.orderIndex());
    }

    @Test
    void inspectionAliasesAndCoordinateFallbackStillWork() throws Exception {
        assertAliases(mapper::readValue);
        assertAliases(legacyMapper::readValue);
        var quote = InspectionQuoteResponse.builder().baseInspectionFee(new BigDecimal("200000")).build();
        assertEquals(200000, mapper.readTree(mapper.writeValueAsString(quote)).get("baseFee").asInt());
        assertEquals(200000, legacyMapper.readTree(legacyMapper.writeValueAsString(quote)).get("baseFee").asInt());
    }

    private void assertAliases(Reader reader) throws Exception {
        for (String consent : new String[]{"buyerConsentZin", "buyerConsentZinLoss"}) {
            var buyer = reader.read("{\"" + consent + "\":true,\"buyerDeliveryAddress\":{\"latitude\":21,\"longitude\":105}}",
                    InspectionBuyRequest.class);
            assertTrue(buyer.buyerConsentZin());
            assertEquals(new BigDecimal("21"), buyer.buyerLatitude());
            assertEquals(new BigDecimal("105"), buyer.buyerLongitude());
        }
        var buyer = reader.read("{\"buyerConsentZin\":true,\"buyerLatitude\":20,\"buyerDeliveryAddress\":{\"latitude\":21}}", InspectionBuyRequest.class);
        assertEquals(new BigDecimal("20"), buyer.buyerLatitude());
        for (String consent : new String[]{"sellerConsentZin", "sellerConsentZinLoss"}) {
            for (String reason : new String[]{"rejectReason", "rejectionReason"}) {
                var seller = reader.read("{\"" + consent + "\":true,\"" + reason + "\":\"Unavailable\"}",
                        InspectionSellerRespondRequest.class);
                assertTrue(seller.sellerConsentZin());
                assertEquals("Unavailable", seller.rejectionReason());
            }
        }
    }

    @Test
    void booleanResponsePropertiesKeepTheirExistingJsonNames() throws Exception {
        var notification = NotificationResponseDTO.builder().isRead(true).build();
        var seal = InspectionSealVerificationResponse.builder().isOfficialSecondLifeSeal(true).build();
        for (String json : new String[]{mapper.writeValueAsString(notification), legacyMapper.writeValueAsString(notification)}) {
            var tree = mapper.readTree(json);
            assertTrue(tree.get("read").asBoolean());
            assertFalse(tree.has("isRead"));
        }
        for (String json : new String[]{mapper.writeValueAsString(seal), legacyMapper.writeValueAsString(seal)}) {
            var tree = mapper.readTree(json);
            assertTrue(tree.get("officialSecondLifeSeal").asBoolean());
            assertFalse(tree.has("isOfficialSecondLifeSeal"));
        }
    }

    @Test
    void messageValidationKeepsTheFourThousandCharacterLimit() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new AiChatRequest(null, null, "a".repeat(4000), null)).isEmpty());
            assertFalse(validator.validate(new AiChatRequest(null, null, "a".repeat(4001), null)).isEmpty());
            assertFalse(validator.validate(new AiChatRequest(null, null, " ", null)).isEmpty());
        }
    }

    @Test
    void multipartModelAttributesBindRecordComponentsAndFiles() throws Exception {
        var controller = new BindingController();
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        var image = new MockMultipartFile("images", "photo.jpg", "image/jpeg", new byte[]{1, 2});
        UUID postId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        mvc.perform(multipart("/dto/chat").file(image).param("postId", postId.toString()).param("message", "Describe"))
                .andExpect(status().isOk());
        assertEquals(postId, controller.chat.postId());
        assertEquals("Describe", controller.chat.message());
        assertArrayEquals(image.getBytes(), controller.chat.images().getFirst().getBytes());
        mvc.perform(multipart("/dto/post").file(image).param("categoryId", categoryId.toString()).param("mode", "manual"))
                .andExpect(status().isOk());
        assertEquals(categoryId, controller.post.categoryId());
        assertEquals("manual", controller.post.mode());
        assertArrayEquals(image.getBytes(), controller.post.images().getFirst().getBytes());
    }

    @FunctionalInterface
    private interface Reader {
        <T> T read(String json, Class<T> type) throws Exception;
    }

    @RestController
    static class BindingController {
        private AiChatRequest chat;
        private PostInitRequest post;

        @PostMapping("/dto/chat")
        void chat(@Valid @ModelAttribute AiChatRequest request) { chat = request; }

        @PostMapping("/dto/post")
        void post(@Valid @ModelAttribute PostInitRequest request) { post = request; }
    }
}

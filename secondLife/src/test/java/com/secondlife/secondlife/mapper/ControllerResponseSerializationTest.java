package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.dto.response.TopupPackagePriceResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.ShipmentEvent;
import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.mapper.CategoryQuestionTemplateMapper;
import com.secondlife.secondlife.mapper.ShipmentEventMapper;
import com.secondlife.secondlife.mapper.TopupPackageMapper;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final com.fasterxml.jackson.databind.ObjectMapper legacyMapper =
            new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void topupPackageDtoUsesCalculatedPricingSaleStateAndNullableDescription() throws Exception {
        TopupPackage entity = new TopupPackage();
        entity.setId(UUID.randomUUID());
        entity.setName("Premium");
        entity.setDescription("Monthly credits");
        entity.setPostCredits(10);
        entity.setChatCredits(20);
        entity.setValuationCredits(30);
        entity.setPrice(new BigDecimal("100000.00"));
        entity.setDiscountPercentage(12.5);
        TopupPackageMapper mapper = MapperTestSupport.mapper(TopupPackageMapper.class);

        var pricing = new TopupPackagePriceResponse(true, new BigDecimal("300000.00"),
                new BigDecimal("66.67"), new BigDecimal("20000"), new BigDecimal("2000"), new BigDecimal("2000"));
        var response = objectMapper.readTree(objectMapper.writeValueAsString(mapper.toResponse(entity, pricing)));
        assertEquals(entity.getId().toString(), response.path("id").asText());
        assertEquals("66.67", response.path("discountPercentage").asText());
        assertEquals(true, response.path("active").asBoolean());
        assertEquals(0L, response.path("packageVersion").asLong());
        assertEquals(0, response.path("price").decimalValue().compareTo(new BigDecimal("100000.00")));
        entity.setDescription(null);
        assertEquals(null, mapper.toResponse(entity, pricing).description());
    }

    @Test
    void categoryTemplateDtoKeepsEveryPreviouslySerializedFieldAndNullableItemId() throws Exception {
        CategoryQuestionTemplate entity = new CategoryQuestionTemplate();
        entity.setId(UUID.randomUUID());
        entity.setCategoryId(UUID.randomUUID());
        entity.setItemId(UUID.randomUUID());
        entity.setTemplateText("Describe the condition\nInclude warranty details");
        CategoryQuestionTemplateMapper mapper = MapperTestSupport.mapper(CategoryQuestionTemplateMapper.class);

        assertSameJson(entity, mapper.toResponse(entity));
        entity.setItemId(null);
        assertSameJson(entity, mapper.toResponse(entity));
    }

    @Test
    void shipmentEventDtoKeepsTheRawPayloadAndNullableStatusAndReason() throws Exception {
        ShipmentEvent entity = new ShipmentEvent();
        entity.setId(UUID.randomUUID());
        entity.setShipmentId(UUID.randomUUID());
        entity.setEventKey("carrier-event-123");
        entity.setType("STATUS_CHANGED");
        entity.setStatus("DELIVERED");
        entity.setOccurredAt(Instant.parse("2026-01-01T12:34:56Z"));
        entity.setReason("Completed delivery");
        entity.setPayload("{\"orderId\":\"carrier-123\",\"data\":{\"status\":\"COMPLETED\"}}");
        ShipmentEventMapper mapper = MapperTestSupport.mapper(ShipmentEventMapper.class);

        assertSameJson(entity, mapper.toResponse(entity));
        entity.setStatus(null);
        entity.setReason(null);
        assertSameJson(entity, mapper.toResponse(entity));
        assertEquals(entity.getPayload(), mapper.toResponse(entity).payload());
    }

    private void assertSameJson(Object entity, Object response) throws Exception {
        assertEquals(objectMapper.readTree(objectMapper.writeValueAsString(entity)),
                objectMapper.readTree(objectMapper.writeValueAsString(response)));
        assertEquals(legacyMapper.readTree(legacyMapper.writeValueAsString(entity)),
                legacyMapper.readTree(legacyMapper.writeValueAsString(response)));
    }
}

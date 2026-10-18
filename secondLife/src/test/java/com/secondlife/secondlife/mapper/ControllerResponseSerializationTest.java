package com.secondlife.secondlife.mapper;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.ShipmentEvent;
import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.mapper.CategoryQuestionTemplateMapper;
import com.secondlife.secondlife.mapper.ShipmentEventMapper;
import com.secondlife.secondlife.mapper.TopupPackageMapper;
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
    void topupPackageDtoKeepsEveryPreviouslySerializedFieldAndNullableDescription() throws Exception {
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

        assertSameJson(entity, mapper.toResponse(entity));
        entity.setDescription(null);
        assertSameJson(entity, mapper.toResponse(entity));
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

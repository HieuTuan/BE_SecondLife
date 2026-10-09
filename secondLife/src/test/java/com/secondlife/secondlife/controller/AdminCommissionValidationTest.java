package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.controller.admin.AdminCommissionController;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.CommissionService;
import com.secondlife.secondlife.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminCommissionValidationTest {
    private static final String BASE = "/api/admin/commission-rules";
    private static final UUID ID = UUID.randomUUID();
    private final CommissionService service = mock(CommissionService.class);
    private final SettlementService settlements = mock(SettlementService.class);
    private final CurrentUserProvider current = mock(CurrentUserProvider.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new AdminCommissionController(service, settlements, current))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
            .build();

    private String payload(String field, Object value) {
        var body = mapper.createObjectNode();
        body.put("name", "Default commission"); body.put("rate", 0.05);
        body.put("minCommission", 10000); body.put("maxCommission", 100000);
        body.put("active", true); body.put("reason", "Configure commission policy");
        body.set(field, mapper.valueToTree(value));
        return mapper.writeValueAsString(body);
    }

    static Stream<Object[]> invalidValues() {
        return Stream.of(new Object[]{"rate", "0.05"}, new Object[]{"rate", ""},
                new Object[]{"rate", 5}, new Object[]{"rate", -0.01},
                new Object[]{"rate", 0.1234567}, new Object[]{"rate", null},
                new Object[]{"minCommission", "10000"}, new Object[]{"minCommission", -1},
                new Object[]{"minCommission", 1.001}, new Object[]{"minCommission", null},
                new Object[]{"minCommission", new java.math.BigDecimal("10000000000000000")},
                new Object[]{"maxCommission", ""}, new Object[]{"maxCommission", "100000"},
                new Object[]{"maxCommission", 0}, new Object[]{"maxCommission", 9999},
                new Object[]{"maxCommission", -1}, new Object[]{"maxCommission", 10000.001},
                new Object[]{"active", "true"}, new Object[]{"active", 1},
                new Object[]{"active", null}, new Object[]{"name", 123},
                new Object[]{"name", " "}, new Object[]{"name", "a".repeat(151)},
                new Object[]{"reason", true}, new Object[]{"reason", " "},
                new Object[]{"reason", "a".repeat(1001)}, new Object[]{"categoryId", ID.toString()});
    }

    @ParameterizedTest @MethodSource("invalidValues")
    void createAndUpdateRejectInvalidFieldBeforeService(String field, Object value) throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post(BASE), put(BASE + "/" + ID)}) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(payload(field, value)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").isNotEmpty());
        }
        verifyNoInteractions(service, settlements, current);
    }

    @ParameterizedTest
    @CsvSource({"/api/admin/commission-rules/{id}/deactivate", "/api/admin/orders/{id}/commission-snapshot"})
    void reasonEndpointsRejectWrongTypeAndUnknownFields(String path) throws Exception {
        for (String body : new String[]{"{\"reason\":123}", "{\"reason\":\" \"}",
                "{\"reason\":\"Audit reason\",\"unexpected\":true}"}) {
            mvc.perform(post(path.replace("{id}", ID.toString())).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service, settlements, current);
    }

    @Test void optionalCapAndZeroCapAreAcceptedAsNumbers() throws Exception {
        when(current.resolveAdminId(any())).thenReturn(ID);
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(payload("maxCommission", null)))
                .andExpect(status().isCreated());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Zero fee","rate":0,"minCommission":0,"maxCommission":0,
                 "active":false,"reason":"Disable fee"}
                """))
                .andExpect(status().isCreated());
        verify(service, times(2)).create(eq(ID), any());
    }

    @Test void missingCapIsAcceptedButMissingRequiredFieldIsRejected() throws Exception {
        when(current.resolveAdminId(any())).thenReturn(ID);
        var body = mapper.readTree(payload("maxCommission", null)).deepCopy();
        ((tools.jackson.databind.node.ObjectNode) body).remove("maxCommission");
        mvc.perform(put(BASE + "/" + ID).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        verify(service).update(eq(ID), eq(ID), any());
        clearInvocations(service, current);
        ((tools.jackson.databind.node.ObjectNode) body).remove("minCommission");
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("minCommission"));
        verifyNoInteractions(service, current);
    }

    @Test void brokenJsonReturns400() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"minCommission\":,\"maxCommission\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service, settlements, current);
    }
}

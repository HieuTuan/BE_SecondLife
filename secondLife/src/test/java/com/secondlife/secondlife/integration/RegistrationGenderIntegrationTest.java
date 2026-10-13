package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.service.NotificationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIf("databaseAvailable") @SpringBootTest @AutoConfigureMockMvc
@TestPropertySource(locations="classpath:application-test.properties",properties={
        "spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=none","app.seeder.enabled=false"})
class RegistrationGenderIntegrationTest {
    private static final GhnTestDatabase database=new GhnTestDatabase("GHN_FLOW_TEST_JDBC_URL");
    static boolean databaseAvailable() { return GhnTestDatabase.available("GHN_FLOW_TEST_JDBC_URL"); }
    @AfterAll static void closeDatabase() { database.close(); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",database::getJdbcUrl); registry.add("spring.datasource.username",database::getUsername);
        registry.add("spring.datasource.password",database::getPassword);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean NotificationService notifications;

    @ParameterizedTest @ValueSource(strings={"MALE","FEMALE","OTHER"})
    void registrationPersistsSelectedGenderAndLoginAndProfileReturnIt(String gender) throws Exception {
        String email="gender-"+UUID.randomUUID()+"@example.test";
        var result=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email",email,"password","Password@123","fullName","Nguyễn Test","gender",gender))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.user.gender").value(gender)).andReturn();
        var data=mapper.readTree(result.getResponse().getContentAsString()).path("data"); UUID id=UUID.fromString(data.path("user").path("id").asText());
        assertEquals(gender,jdbc.queryForObject("SELECT gender FROM user_profiles WHERE user_id=?",String.class,id));
        String token="Bearer "+data.path("accessToken").asText();
        mvc.perform(get("/api/v1/users/me").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.gender").value(gender));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email",email,"password","Password@123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.gender").value(gender))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
        mvc.perform(patch("/api/v1/users/me").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"gender\":\"OTHER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.gender").value("OTHER"));
        assertEquals("OTHER",jdbc.queryForObject("SELECT gender FROM user_profiles WHERE user_id=?",String.class,id));
        assertThrows(DataIntegrityViolationException.class,() -> jdbc.update("UPDATE user_profiles SET gender='INVALID' WHERE user_id=?",id));
    }
    @Test void omittedGenderRemainsNullAndCanBeSelectedLater() throws Exception {
        var result=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email","unspecified-"+UUID.randomUUID()+"@example.test","password","Password@123","fullName","Nguyễn Test"))))
                .andExpect(status().isCreated()).andReturn();
        var data=mapper.readTree(result.getResponse().getContentAsString()).path("data"); UUID id=UUID.fromString(data.path("user").path("id").asText());
        assertNull(jdbc.queryForObject("SELECT gender FROM user_profiles WHERE user_id=?",String.class,id));
        mvc.perform(patch("/api/v1/users/me").header("Authorization","Bearer "+data.path("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"gender\":\"FEMALE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.gender").value("FEMALE"));
    }
}

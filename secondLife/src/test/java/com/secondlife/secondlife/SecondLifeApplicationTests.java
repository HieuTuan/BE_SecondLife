package com.secondlife.secondlife;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class SecondLifeApplicationTests {

    @MockitoBean
    private DataSource dataSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthAndOpenApiArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/seller-onboarding/me'].put").exists())
                .andExpect(jsonPath("$.paths.keys()").value(everyItem(anyOf(
                        startsWith("/api/v1/"), startsWith("/api/auth/"), startsWith("/api/ai/")))));
    }

    @Test
    void protectedApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/seller-verifications"))
                .andExpect(status().isUnauthorized());
    }

}

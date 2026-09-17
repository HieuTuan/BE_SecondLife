package com.secondlife.secondlife;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class SecondLifeApplicationTests {

    @MockitoBean
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

}

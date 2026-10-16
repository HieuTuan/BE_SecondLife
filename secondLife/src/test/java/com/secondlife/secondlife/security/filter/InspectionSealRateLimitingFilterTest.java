package com.secondlife.secondlife.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class InspectionSealRateLimitingFilterTest {

    private InspectionSealRateLimitingFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new InspectionSealRateLimitingFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Cho phép tối đa 30 requests/phút từ cùng một IP")
    void testRateLimitWithinThreshold() throws ServletException, IOException {
        String testIp = "192.168.1.100";

        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/inspection/seal/SL-SEAL-12345");
            request.setRemoteAddr(testIp);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);
            assertEquals(200, response.getStatus());
        }

        verify(filterChain, times(30)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Chặn request thứ 31 với mã lỗi 429 Too Many Requests")
    void testRateLimitExceeded() throws ServletException, IOException {
        String testIp = "192.168.1.101";

        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/inspection/seal/SL-SEAL-12345");
            request.setRemoteAddr(testIp);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, filterChain);
        }

        // Request thứ 31
        MockHttpServletRequest request31 = new MockHttpServletRequest("GET", "/api/v1/inspection/seal/SL-SEAL-12345");
        request31.setRemoteAddr(testIp);
        MockHttpServletResponse response31 = new MockHttpServletResponse();

        filter.doFilter(request31, response31, filterChain);

        assertEquals(429, response31.getStatus());
        // filterChain không được gọi thêm cho request thứ 31
        verify(filterChain, times(30)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Không áp dụng rate limit cho các endpoint khác hoặc method không phải GET")
    void testBypassNonSealEndpoints() throws ServletException, IOException {
        MockHttpServletRequest postRequest = new MockHttpServletRequest("POST", "/api/v1/inspection/seal/scan");
        postRequest.setRemoteAddr("192.168.1.102");
        MockHttpServletResponse postResponse = new MockHttpServletResponse();

        filter.doFilter(postRequest, postResponse, filterChain);
        assertEquals(200, postResponse.getStatus());

        MockHttpServletRequest otherRequest = new MockHttpServletRequest("GET", "/api/v1/posts");
        otherRequest.setRemoteAddr("192.168.1.102");
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();

        filter.doFilter(otherRequest, otherResponse, filterChain);
        assertEquals(200, otherResponse.getStatus());
    }
}

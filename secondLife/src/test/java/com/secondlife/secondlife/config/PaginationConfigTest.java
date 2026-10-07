package com.secondlife.secondlife.config;

import com.secondlife.secondlife.controller.admin.AdminRbacController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(PaginationConfig.class)
            .withPropertyValues("app.pagination.default-page=2", "app.pagination.default-size=7", "app.pagination.max-size=10");

    @Test
    void administrativeEndpointRetainsSortAndUsesConfiguredPageAndSize() {
        runner.run(context -> {
            PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
            context.getBean(PageableHandlerMethodArgumentResolverCustomizer.class).customize(resolver);
            MethodParameter parameter = new MethodParameter(AdminRbacController.class
                    .getMethod("getRolePermissionAudit", String.class, Pageable.class), 1);
            Pageable page = resolver.resolveArgument(parameter, null,
                    new ServletWebRequest(new MockHttpServletRequest()), null);
            assertThat(page.getPageNumber()).isEqualTo(2);
            assertThat(page.getPageSize()).isEqualTo(7);
            assertThat(page.getSort().getOrderFor("changedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        });
    }

    @Test
    void maximumSizeLimitsClientRequestedPageSize() {
        runner.run(context -> {
            PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();
            context.getBean(PageableHandlerMethodArgumentResolverCustomizer.class).customize(resolver);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addParameter("size", "1000");
            MethodParameter parameter = new MethodParameter(AdminRbacController.class
                    .getMethod("getRolePermissionAudit", String.class, Pageable.class), 1);
            Pageable page = resolver.resolveArgument(parameter, null, new ServletWebRequest(request), null);
            assertThat(page.getPageSize()).isEqualTo(10);
        });
    }

    @Test
    void invalidDefaultSizeFailsStartup() {
        runner.withPropertyValues("app.pagination.default-size=0").run(context -> assertThat(context).hasFailed());
    }
}

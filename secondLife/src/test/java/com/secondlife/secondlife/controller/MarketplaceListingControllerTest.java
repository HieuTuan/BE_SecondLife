package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.service.MarketplaceListingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketplaceListingControllerTest {
    private final MarketplaceListingService service = mock(MarketplaceListingService.class);

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner()
                .withBean(MarketplaceListingService.class, () -> service)
                .withBean(MarketplaceListingController.class)
                .withPropertyValues("app.pagination.default-page=2", "app.pagination.default-size=3",
                        "app.pagination.max-size=5");
    }

    @Test
    void oldSwaggerSortParameterIsIgnored() {
        when(service.list(isNull(), isNull(), any())).thenAnswer(call -> {
            Pageable page = call.getArgument(2);
            assertEquals(0, page.getPageNumber());
            assertEquals(1, page.getPageSize());
            assertEquals(Sort.by("publishedAt").descending(), page.getSort());
            return new PageImpl<>(List.of(), page, 0);
        });
        context().run(ctx -> mvc(ctx).perform(get("/api/v1/listings")
                .param("page", "0").param("size", "1").param("sort", "[\"asc\"]"))
                .andExpect(status().isOk()));
        verify(service).list(isNull(), isNull(), any());
    }

    @Test
    void paginationDefaultsComeFromConfiguration() {
        when(service.list(isNull(), isNull(), any())).thenAnswer(call -> {
            Pageable page = call.getArgument(2);
            assertEquals(2, page.getPageNumber());
            assertEquals(3, page.getPageSize());
            return new PageImpl<>(List.of(), page, 0);
        });
        context().run(ctx -> mvc(ctx).perform(get("/api/v1/listings")).andExpect(status().isOk()));
    }

    @Test
    void invalidPaginationReturns400BeforeQuerying() {
        context().run(ctx -> {
            mvc(ctx).perform(get("/api/v1/listings").param("page", "-1")).andExpect(status().isBadRequest());
            mvc(ctx).perform(get("/api/v1/listings").param("size", "6")).andExpect(status().isBadRequest());
        });
        verifyNoInteractions(service);
    }

    private MockMvc mvc(ApplicationContext context) {
        var mvc = MockMvcBuilders.standaloneSetup(context.getBean(MarketplaceListingController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        var factory = (ConfigurableBeanFactory) context.getAutowireCapableBeanFactory();
        factory.setBeanExpressionResolver(new StandardBeanExpressionResolver());
        factory.addEmbeddedValueResolver(context.getEnvironment()::resolveRequiredPlaceholders);
        var adapter = mvc.getDispatcherServlet().getWebApplicationContext().getBean(RequestMappingHandlerAdapter.class);
        adapter.setArgumentResolvers(adapter.getArgumentResolvers().stream()
                .map(resolver -> resolver instanceof RequestParamMethodArgumentResolver
                        ? new RequestParamMethodArgumentResolver(factory, false) : resolver).toList());
        return mvc;
    }
}

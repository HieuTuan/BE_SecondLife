package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.AiValuationService;
import com.secondlife.secondlife.service.ListingDraftService;
import com.secondlife.secondlife.service.ListingReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListingPaginationConfigurationTest {
    private final ListingReviewService reviews = mock(ListingReviewService.class);
    private final AiValuationService valuations = mock(AiValuationService.class);
    private final ListingDraftService drafts = mock(ListingDraftService.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);

    private ApplicationContextRunner controllers() {
        return new ApplicationContextRunner()
                .withBean(ListingReviewService.class, () -> reviews)
                .withBean(AiValuationService.class, () -> valuations)
                .withBean(ListingDraftService.class, () -> drafts)
                .withBean(CurrentUserProvider.class, () -> currentUser)
                .withBean(StaffListingController.class)
                .withBean(ListingValuationController.class)
                .withPropertyValues("app.pagination.default-page=2", "app.pagination.default-size=3",
                        "app.pagination.max-size=5");
    }

    @Test
    void staffQueueUsesConfiguredPaginationDefaults() {
        when(reviews.queue(any())).thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(0), 20));
        controllers().run(context -> mockMvc(context.getBean(StaffListingController.class), context)
                .perform(get("/api/staff/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.size").value(3)));
    }

    @Test
    void valuationHistoryUsesConfiguredPaginationDefaults() {
        UUID postId = UUID.randomUUID();
        when(valuations.history(any(), eq(postId), any()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 20));
        controllers().run(context -> mockMvc(context.getBean(ListingValuationController.class), context)
                .perform(get("/api/v1/posts/" + postId + "/ai-price-estimation/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(2))
                .andExpect(jsonPath("$.data.size").value(3)));
    }

    @Test
    void staffQueueRejectsPageSizeAboveConfiguredMaximum() {
        controllers().run(context -> assertThrows(BadRequestException.class,
                () -> context.getBean(StaffListingController.class).queue(0, 6)));
    }

    @Test
    void valuationHistoryRejectsPageSizeAboveConfiguredMaximum() {
        controllers().run(context -> assertThrows(BadRequestException.class,
                () -> context.getBean(ListingValuationController.class).history(null, UUID.randomUUID(), 0, 6)));
    }

    private MockMvc mockMvc(Object controller, ApplicationContext context) {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        ConfigurableBeanFactory beanFactory = (ConfigurableBeanFactory) context.getAutowireCapableBeanFactory();
        beanFactory.addEmbeddedValueResolver(context.getEnvironment()::resolveRequiredPlaceholders);
        RequestMappingHandlerAdapter adapter = mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerAdapter.class);
        adapter.setArgumentResolvers(adapter.getArgumentResolvers().stream()
                .map(resolver -> resolver instanceof RequestParamMethodArgumentResolver
                        ? new RequestParamMethodArgumentResolver(beanFactory, false) : resolver).toList());
        return mvc;
    }
}

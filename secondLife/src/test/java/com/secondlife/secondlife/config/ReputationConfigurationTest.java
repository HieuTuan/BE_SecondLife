package com.secondlife.secondlife.config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
class ReputationConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(ReputationConfiguration.class);
    @Test void missingMinimumMustFailStartup() {
        runner.withPropertyValues("app.reputation.handover-deadline-hours=48","app.reputation.max-evidence-images=6").run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void invalidDeadlineMustFailStartup() {
        runner.withPropertyValues("app.reputation.handover-deadline-hours=0","app.reputation.min-evaluated-orders=5","app.reputation.max-evidence-images=6")
            .run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void completeValidConfigurationStartsSuccessfully() {
        runner.withPropertyValues("app.reputation.handover-deadline-hours=48","app.reputation.min-evaluated-orders=5","app.reputation.max-evidence-images=6")
            .run(c->{assertNull(c.getStartupFailure()); assertEquals(5,c.getBean(ReputationProperties.class).minEvaluatedOrders());});
    }
}

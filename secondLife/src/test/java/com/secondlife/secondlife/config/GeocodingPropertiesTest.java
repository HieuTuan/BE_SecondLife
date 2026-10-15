package com.secondlife.secondlife.config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;
class GeocodingPropertiesTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(Config.class).withPropertyValues(
            "app.geocoding.geoapify.enabled=true","app.geocoding.geoapify.base-url=https://api.geoapify.com/v1/geocode/reverse",
            "app.geocoding.geoapify.fallback-base-url=https://nominatim.openstreetmap.org/reverse",
            "app.geocoding.geoapify.fallback-user-agent=SecondLife-Test/1.0", "app.geocoding.geoapify.language=vi","app.geocoding.geoapify.timeout-ms=5000","app.geocoding.geoapify.max-response-characters=1048576");
    @Test void enabledWithoutKeyFailsStartup() { runner.run(context->assertThat(context).hasFailed()); }
    @Test void enabledWithKeyStarts() { runner.withPropertyValues("app.geocoding.geoapify.api-key=test-key").run(context->assertThat(context).hasNotFailed()); }
    @Test void disabledWithExplicitBlankKeyStarts() { runner.withPropertyValues("app.geocoding.geoapify.enabled=false","app.geocoding.geoapify.api-key=").run(context->assertThat(context).hasNotFailed()); }
    @Test void unapprovedGatewayFailsStartup() { runner.withPropertyValues("app.geocoding.geoapify.api-key=test-key","app.geocoding.geoapify.base-url=https://untrusted.example").run(context->assertThat(context).hasFailed()); }
    @Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(GeocodingProperties.class) static class Config {}
}

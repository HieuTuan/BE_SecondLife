package com.secondlife.secondlife.seeder;

import com.secondlife.secondlife.service.CatalogSeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.catalog.seed-enabled", havingValue = "true")
public class CatalogDataSeeder implements ApplicationRunner {
    private final CatalogSeedService catalog;
    @Override public void run(ApplicationArguments args) { catalog.seedDefaults(); }
}

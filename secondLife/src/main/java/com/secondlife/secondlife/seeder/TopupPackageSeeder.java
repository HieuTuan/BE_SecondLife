package com.secondlife.secondlife.seeder;

import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.repository.TopupPackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.seeder.enabled", havingValue = "true")
public class TopupPackageSeeder implements ApplicationRunner {

    private final TopupPackageRepository topupPackageRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (topupPackageRepository.count() > 0) {
            log.info("Topup packages already seeded. Skipping...");
            return;
        }

        log.info("Starting Topup Packages Seeding...");

        List<TopupPackage> packages = List.of(
                // Combo Packages
                createPackage("Basic Combo", "2 posts and 10 AI chat credits", 2, 10, new BigDecimal("59000"), 0.0),
                createPackage("Standard Combo", "5 posts and 25 AI chat credits", 5, 25, new BigDecimal("129000"), 12.5),
                createPackage("Pro Combo", "10 posts and 50 AI chat credits", 10, 50, new BigDecimal("249000"), 15.6),
                createPackage("Premium Combo", "20 posts and 100 AI chat credits", 20, 100, new BigDecimal("449000"), 24.0),

                // Post Only Packages
                createPackage("Single Post", "Add 1 new post credit", 1, 0, new BigDecimal("20000"), 0.0),
                createPackage("5 Posts", "Add 5 new post credits (Save 11%)", 5, 0, new BigDecimal("89000"), 11.0),
                createPackage("10 Posts", "Add 10 new post credits (Save 20.5%)", 10, 0, new BigDecimal("159000"), 20.5),

                // Chat Only Packages
                createPackage("10 AI Chats", "Add 10 AI chat credits", 0, 10, new BigDecimal("20000"), 0.0),
                createPackage("50 AI Chats", "Add 50 AI chat credits (Save 21%)", 0, 50, new BigDecimal("79000"), 21.0),
                createPackage("100 AI Chats", "Add 100 AI chat credits (Save 30.5%)", 0, 100, new BigDecimal("139000"), 30.5)
        );

        topupPackageRepository.saveAll(packages);
        log.info("Successfully seeded {} Topup Packages.", packages.size());
    }

    private TopupPackage createPackage(String name, String description, int postCredits, int chatCredits, BigDecimal price, double discountPercentage) {
        TopupPackage pkg = new TopupPackage();
        pkg.setName(name);
        pkg.setDescription(description);
        pkg.setPostCredits(postCredits);
        pkg.setChatCredits(chatCredits);
        pkg.setPrice(price);
        pkg.setDiscountPercentage(discountPercentage);
        return pkg;
    }
}

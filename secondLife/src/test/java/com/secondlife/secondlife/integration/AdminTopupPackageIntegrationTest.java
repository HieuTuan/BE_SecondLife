package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.TopupPackagePurchaseRequest;
import com.secondlife.secondlife.dto.request.TopupPackageRequest;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.CreditBalanceRepository;
import com.secondlife.secondlife.repository.TopupPackageRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.AdminTopupPackageService;
import com.secondlife.secondlife.service.CreditService;
import com.secondlife.secondlife.service.TopupPackagePurchaseService;
import com.secondlife.secondlife.service.TopupPackageQueryService;
import com.secondlife.secondlife.service.WalletService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class AdminTopupPackageIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AdminTopupPackageService admin;
    @Autowired TopupPackageQueryService catalog;
    @Autowired TopupPackageRepository packages;
    @Autowired UserRepository users;
    @Autowired CreditService credits;
    @Autowired WalletService wallets;
    @Autowired JdbcTemplate jdbc;
    @Autowired TopupPackagePurchaseService purchases;
    @MockitoSpyBean CreditBalanceRepository balanceRepository;

    @AfterEach
    void resetBalanceSpy() { reset(balanceRepository); }

    @BeforeEach
    void prices() {
        jdbc.update("UPDATE credit_pricing_rules SET unit_price = 20000, active = true WHERE credit_type = 'LISTING'");
        jdbc.update("UPDATE credit_pricing_rules SET unit_price = 2000, active = true WHERE credit_type IN ('AI_CHAT','VALUATION')");
    }

    private TopupPackagePurchaseRequest buyRequest(UUID packageId) {
        var pkg = admin.getPackage(packageId);
        return new TopupPackagePurchaseRequest(UUID.randomUUID(), pkg.packageVersion(), pkg.price());
    }

    private TopupPackageRequest request(String name, String price, int postCredits, int chatCredits, int valuations) {
        return new TopupPackageRequest(name, null, postCredits, chatCredits, valuations,
                new BigDecimal(price), true);
    }

    @Test
    void crudPersistsAndExistingCatalogSeesChanges() {
        var created = admin.createPackage(request("CRUD " + UUID.randomUUID(), "59000", 2, 10, 2));
        UUID id = created.id();
        assertEquals(0, packages.findById(id).orElseThrow().getPrice().compareTo(new BigDecimal("59000")));
        assertTrue(catalog.getPackages().stream().anyMatch(pkg -> pkg.id().equals(id)));
        var updated = admin.updatePackage(id, request("Edited", "65000.50", 3, 12, 4));
        assertEquals(id, updated.id());
        assertEquals(0, admin.getPackage(id).price().compareTo(new BigDecimal("65000.50")));
        assertEquals(12, packages.findById(id).orElseThrow().getChatCredits());
        assertTrue(admin.getPackages().stream().anyMatch(pkg -> pkg.id().equals(id)));
        admin.deletePackage(id);
        assertTrue(packages.existsById(id));
        assertFalse(admin.getPackage(id).active());
        assertFalse(catalog.getPackages().stream().anyMatch(pkg -> pkg.id().equals(id)));
        assertTrue(admin.setActive(id, true).active());
        UUID missing = UUID.randomUUID();
        assertThrows(NotFoundException.class, () -> admin.getPackage(missing));
        assertThrows(NotFoundException.class, () -> admin.updatePackage(missing, request("Missing", "1000", 1, 0, 0)));
        assertThrows(NotFoundException.class, () -> admin.deletePackage(missing));
    }

    @Test
    void editingAndStoppingPurchasedPackageKeepsMoneyCreditAndHistory() {
        UUID userId = users.saveAndFlush(new User("topup-" + UUID.randomUUID() + "@test.invalid",
                "test-password-hash", AccountStatus.ACTIVE)).getId();
        UUID walletId = wallets.createWalletForUser(userId).getId();
        jdbc.update("UPDATE user_wallets SET balance = 1000000 WHERE id = ?", walletId);
        UUID packageId = admin.createPackage(request("Purchase", "59000", 2, 10, 2)).id();
        purchases.purchase(userId, packageId, buyRequest(packageId));
        assertEquals(0, wallets.getWalletByUserId(userId).getBalance().compareTo(new BigDecimal("941000")));
        admin.updatePackage(packageId, request("Updated purchase", "65000", 3, 12, 4));
        var request = buyRequest(packageId);
        var purchased = purchases.purchase(userId, packageId, request);
        assertEquals(5, purchased.postBalanceAfter());
        assertEquals(22, purchased.chatBalanceAfter());
        assertEquals(6, purchased.valuationBalanceAfter());
        admin.deletePackage(packageId);
        assertEquals(5, credits.getUserCredit(userId).postCredits());
        assertEquals(0, wallets.getWalletByUserId(userId).getBalance().compareTo(new BigDecimal("876000")));
        assertEquals(Set.of(new BigDecimal("-59000.00"), new BigDecimal("-65000.00")),
                Set.copyOf(jdbc.queryForList(
                        "SELECT amount FROM wallet_transactions WHERE wallet_id = ? AND reference_id = ?",
                        BigDecimal.class, walletId, packageId)));
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Integer.class, userId));
        assertEquals(purchased.id(), purchases.purchase(userId, packageId, request).id());
        assertThrows(ConflictException.class, () -> purchases.purchase(userId, packageId, buyRequest(packageId)));
        assertEquals(purchased, purchases.getMyPurchase(userId, purchased.id()));
        assertEquals(2, purchases.getMyPurchases(userId, 0, 20).totalElements());
        assertThrows(NotFoundException.class, () -> purchases.getMyPurchase(UUID.randomUUID(), purchased.id()));
        assertEquals(0, wallets.getWalletByUserId(userId).getBalance().compareTo(new BigDecimal("876000")));
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM wallet_transactions WHERE wallet_id = ?", Integer.class, walletId));
    }

    @Test
    void insufficientWalletDoesNotGrantCreditsOrRecordPaymentForNewPackage() {
        UUID userId = users.saveAndFlush(new User("poor-" + UUID.randomUUID() + "@test.invalid",
                "test-password-hash", AccountStatus.ACTIVE)).getId();
        UUID walletId = wallets.createWalletForUser(userId).getId();
        UUID id = admin.createPackage(request("Insufficient balance", "1000", 1, 0, 0)).id();
        assertThrows(BadRequestException.class, () -> purchases.purchase(userId, id, buyRequest(id)));
        assertEquals(0, credits.getUserCredit(userId).postCredits());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Integer.class, userId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM wallet_transactions WHERE wallet_id = ?", Integer.class, walletId));
        assertEquals(0, wallets.getWalletByUserId(userId).getBalance().signum());
        assertEquals(0, purchases.getMyPurchases(userId, 0, 20).totalElements());
    }

    @Test
    void pricesAreDerivedAndMissingRulesNeverInventDiscounts() {
        UUID id = admin.createPackage(request("Derived", "59000", 2, 10, 2)).id();
        assertEquals(new BigDecimal("64000.00"), admin.getPackage(id).referencePrice());
        assertEquals(new BigDecimal("7.81"), admin.getPackage(id).discountPercentage());
        jdbc.update("UPDATE credit_pricing_rules SET unit_price = 3000 WHERE credit_type = 'AI_CHAT'");
        assertEquals(new BigDecimal("74000.00"), admin.getPackage(id).referencePrice());
        jdbc.update("UPDATE credit_pricing_rules SET active = false WHERE credit_type = 'AI_CHAT'");
        assertFalse(admin.getPackage(id).pricingAvailable());
        assertEquals(null, admin.getPackage(id).discountPercentage());
        assertThrows(ConflictException.class, () -> admin.setActive(id, true));
    }

    @Test
    void rejectsEmptyCreditsAndPriceAboveReference() {
        assertThrows(BadRequestException.class, () -> admin.createPackage(request("Empty", "1000", 0, 0, 0)));
        assertThrows(ConflictException.class, () -> admin.createPackage(request("Overpriced", "30000", 1, 0, 0)));
    }

    private UUID fundedUser() {
        UUID userId = users.saveAndFlush(new User("retry-" + UUID.randomUUID() + "@test.invalid",
                "test-password-hash", AccountStatus.ACTIVE)).getId();
        UUID walletId = wallets.createWalletForUser(userId).getId();
        jdbc.update("UPDATE user_wallets SET balance = 1000000 WHERE id = ?", walletId);
        return userId;
    }

    @Test
    void concurrentRetriesChargeAndGrantExactlyOnce() throws Exception {
        UUID userId = fundedUser();
        UUID packageId = admin.createPackage(request("Concurrent", "59000", 2, 10, 2)).id();
        var body = buyRequest(packageId);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = IntStream.range(0, 4).mapToObj(i ->
                    executor.submit(() -> { start.await(); return purchases.purchase(userId, packageId, body); })).toList();
            start.countDown();
            var ids = new HashSet<UUID>();
            for (var task : tasks) ids.add(task.get(30, TimeUnit.SECONDS).id());
            assertEquals(1, ids.size());
        }
        assertEquals(0, wallets.getWalletByUserId(userId).getBalance().compareTo(new BigDecimal("941000")));
        assertEquals(2, credits.getUserCredit(userId).postCredits());
        assertEquals(1, purchases.getMyPurchases(userId, 0, 20).totalElements());
        var changed = new TopupPackagePurchaseRequest(body.requestId(), body.packageVersion(), new BigDecimal("58000"));
        assertThrows(ConflictException.class, () -> purchases.purchase(userId, packageId, changed));
    }

    @Test
    void staleVersionAndPriceAreRejectedBeforeCharging() {
        UUID userId = fundedUser();
        UUID packageId = admin.createPackage(request("Stale", "59000", 2, 10, 2)).id();
        var old = buyRequest(packageId);
        admin.updatePackage(packageId, request("Changed", "60000", 2, 10, 2));
        assertThrows(ConflictException.class, () -> purchases.purchase(userId, packageId, old));
        assertEquals(0, wallets.getWalletByUserId(userId).getBalance().compareTo(new BigDecimal("1000000")));
        assertEquals(0, purchases.getMyPurchases(userId, 0, 20).totalElements());
    }

    @Test
    void creditGrantFailureRollsBackWalletLedgerAndReceiptThenSameRequestCanRetry() {
        UUID userId = fundedUser();
        UUID packageId = admin.createPackage(request("Rollback", "59000", 2, 10, 2)).id();
        var body = buyRequest(packageId);
        doThrow(new IllegalStateException("simulated grant failure"))
                .when(balanceRepository).grant(eq(userId), eq(CreditType.AI_CHAT), anyInt());
        assertThrows(IllegalStateException.class, () -> purchases.purchase(userId, packageId, body));
        assertEquals(0, wallets.getWalletByUserId(userId).getBalance().compareTo(new BigDecimal("1000000")));
        assertEquals(0, credits.getUserCredit(userId).postCredits());
        assertEquals(0, purchases.getMyPurchases(userId, 0, 20).totalElements());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM credit_ledger WHERE user_id = ?", Integer.class, userId));
        reset(balanceRepository);
        assertEquals(59000, purchases.purchase(userId, packageId, body).price().intValueExact());
    }

    @Test
    void sameRequestIdIsScopedToUserAndQuantityChangesInvalidateOldVersion() {
        UUID user = fundedUser();
        UUID other = fundedUser();
        UUID packageId = admin.createPackage(request("Owners", "59000", 2, 10, 2)).id();
        var body = buyRequest(packageId);
        var first = purchases.purchase(user, packageId, body);
        var second = purchases.purchase(other, packageId, body);
        assertFalse(first.id().equals(second.id()));
        admin.updatePackage(packageId, request("New rights", "59000", 3, 10, 2));
        var stale = new TopupPackagePurchaseRequest(UUID.randomUUID(), body.packageVersion(), body.expectedPrice());
        assertThrows(ConflictException.class, () -> purchases.purchase(user, packageId, stale));
        assertEquals(first.id(), purchases.purchase(user, packageId, body).id());
        assertThrows(BadRequestException.class, () -> purchases.getMyPurchases(user, -1, 20));
        assertThrows(BadRequestException.class, () -> purchases.getMyPurchases(user, 0, 101));
    }
}

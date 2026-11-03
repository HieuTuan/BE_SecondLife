package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.integration.ai.AiPriceProvider;
import com.secondlife.secondlife.integration.storage.ReportEvidenceStorage;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.CloudinaryService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.WalletService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf("databaseAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false"
})
class WalletTransactionHistoryIntegrationTest {
    private static final String DATABASE_ENV = "WALLET_HISTORY_TEST_JDBC_URL";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() { return GhnTestDatabase.available(DATABASE_ENV); }
    @AfterAll static void closeDatabase() { database.close(); }
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", database::getJdbcUrl);
        properties.add("spring.datasource.username", database::getUsername);
        properties.add("spring.datasource.password", database::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private UserWalletRepository wallets;
    @Autowired private WalletService service;
    @Autowired private JwtTokenProvider jwt;
    @MockitoBean private AiPriceProvider priceProvider;
    @MockitoBean private CloudinaryService cloudinary;
    @MockitoBean private ReportEvidenceStorage evidenceStorage;
    @MockitoBean private NotificationService notifications;
    @MockitoBean(name = "ollamaChatModel") private ChatModel ollama;
    @MockitoBean(name = "googleGenAiChatModel") private ChatModel google;

    @Test
    void listAndDetailRequireAuthentication() throws Exception {
        mvc.perform(get("/api/v1/wallets/me/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallets/me/transactions/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownDetailsExposeTheActualPostedBalancesAndForeignIdsReturn404() throws Exception {
        User owner = user("BUYER");
        User stranger = user("BUYER");
        UUID reference = UUID.randomUUID();
        service.deposit(owner.getId(), new BigDecimal("100000"), UUID.randomUUID());
        service.processPayment(owner.getId(), new BigDecimal("30000"), reference);
        UUID id = jdbc.queryForObject("SELECT id FROM wallet_transactions WHERE reference_id=?", UUID.class, reference);
        mvc.perform(get("/api/v1/wallets/me/transactions/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.amount").value(-30000))
                .andExpect(jsonPath("$.balanceBefore").value(100000))
                .andExpect(jsonPath("$.balanceAfter").value(70000))
                .andExpect(jsonPath("$.referenceId").value(reference.toString()))
                .andExpect(jsonPath("$.referenceType").value("ORDER"))
                .andExpect(jsonPath("$.direction").value("DEBIT"))
                .andExpect(jsonPath("$.currency").value("VND"))
                .andExpect(jsonPath("$.description").value("Thanh toán đơn hàng từ ví"));
        mvc.perform(get("/api/v1/wallets/me/transactions/" + id).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/wallets/me/transactions/" + UUID.randomUUID())
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void filtersUseInclusiveStartExclusiveEndAndNeverIncludeAnotherWallet() throws Exception {
        User owner = user("BUYER");
        User stranger = user("BUYER");
        Instant from = Instant.parse("2026-10-25T00:00:00Z");
        Instant to = from.plusSeconds(86400);
        UUID included = legacy(owner, "DEPOSIT", "10", from);
        legacy(owner, "DEPOSIT", "20", to);
        legacy(owner, "DEPOSIT", "30", from.minusSeconds(1));
        legacy(owner, "PAYMENT", "-40", from.plusSeconds(1));
        legacy(stranger, "DEPOSIT", "50", from.plusSeconds(1));
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("type", "DEPOSIT").param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(included.toString()));
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("from", to.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("to", from.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void defaultPaginationIsDeterministicForEqualTimesAndLegacyBalancesStayNull() throws Exception {
        User owner = user("SELLER");
        Instant time = Instant.parse("2026-10-25T00:00:00Z");
        UUID first = legacy(owner, "REFUND", "10", time);
        UUID second = legacy(owner, "REFUND", "20", time);
        UUID newest = first.toString().compareTo(second.toString()) > 0 ? first : second;
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(newest.toString()))
                .andExpect(jsonPath("$.items[0].referenceType").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].balanceBefore").value(nullValue()))
                .andExpect(jsonPath("$.items[0].balanceAfter").value(nullValue()));
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isLast").value(true))
                .andExpect(jsonPath("$.items[0].id").value((newest.equals(first) ? second : first).toString()));
    }

    @Test
    void readingEmptyHistoryDoesNotCreateAWallet() throws Exception {
        User owner = user("BUYER");
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertFalse(wallets.findByUserId(owner.getId()).isPresent());
    }

    @Test
    void invalidFiltersAndSortFieldsReturn400() throws Exception {
        String token = bearer(user("BUYER"));
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", token)
                        .param("from", "2026-10-26T00:00:00Z").param("to", "2026-10-25T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", token)
                        .param("sort", "string,desc"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", token)
                        .param("type", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", token)
                        .param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectedWithdrawalPreservesBothCashMovementsAndTheirSnapshots() throws Exception {
        User owner = user("SELLER");
        User admin = user("ADMIN");
        service.deposit(owner.getId(), new BigDecimal("200000"), UUID.randomUUID());
        var withdrawal = service.requestWithdrawal(owner.getId(), new WalletWithdrawalRequest(
                new BigDecimal("50000"), "VCB", "0123456789", "TEST USER"));
        service.reviewWithdrawal(admin.getId(), withdrawal.id(), new WalletWithdrawalReviewRequest("REJECT", "Test"));
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("type", "REFUND"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].referenceType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.items[0].description").value("Hoàn tiền do yêu cầu rút tiền bị từ chối"))
                .andExpect(jsonPath("$.items[0].balanceBefore").value(150000))
                .andExpect(jsonPath("$.items[0].balanceAfter").value(200000));
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("type", "WITHDRAW"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].amount").value(-50000))
                .andExpect(jsonPath("$.items[0].referenceType").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.items[0].balanceBefore").value(200000))
                .andExpect(jsonPath("$.items[0].balanceAfter").value(150000));
    }

    @Test
    void explicitCreditReferenceSurvivesStorageAndMapping() throws Exception {
        User owner = user("SELLER");
        service.deposit(owner.getId(), new BigDecimal("100000"), UUID.randomUUID());
        service.processPayment(owner.getId(), new BigDecimal("10000"), UUID.randomUUID(),
                WalletTransactionReferenceType.CREDIT_PURCHASE);
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("type", "PAYMENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].referenceType").value("CREDIT_PURCHASE"))
                .andExpect(jsonPath("$.items[0].description").value("Mua quyền sử dụng dịch vụ bằng số dư ví"));
    }

    @Test
    void unsupportedWithdrawalPrecisionReturns400WithoutChangingMoney() throws Exception {
        User owner = user("SELLER");
        service.deposit(owner.getId(), new BigDecimal("200000.00"), UUID.randomUUID());
        mvc.perform(post("/api/v1/wallets/withdrawals").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50000.005,"bankCode":"VCB","accountNumber":"0123456789","accountHolderName":"TEST USER"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/wallets/me").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(200000));
        mvc.perform(get("/api/v1/wallets/me/transactions").header("Authorization", bearer(owner))
                        .param("type", "WITHDRAW"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    private User user(String role) {
        User user = new User("wallet-history-" + UUID.randomUUID() + "@example.test", "test-hash", AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        user.addRole(roles.findByCodeWithPermissions(role).orElseThrow());
        return users.saveAndFlush(user);
    }

    private String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }

    private UUID legacy(User user, String type, String amount, Instant time) {
        UUID walletId = service.createWalletForUser(user.getId()).getId();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO wallet_transactions(id,wallet_id,type,amount,created_at) VALUES (?,?,?,?,?)",
                id, walletId, type, new BigDecimal(amount), Timestamp.from(time));
        return id;
    }
}

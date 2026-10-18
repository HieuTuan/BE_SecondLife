package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.PostImage;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MapStructMapperTest {
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void loadGeneratedSpringMappers() {
        context = new AnnotationConfigApplicationContext("com.secondlife.secondlife.mapper");
    }

    @AfterAll
    static void closeContext() {
        context.close();
    }

    @Test
    void postProjectionKeepsSellerAddressAndCopiesImages() {
        User user = user("seller@example.test");
        UserProfile profile = new UserProfile();
        profile.setFullName("Seller");
        profile.setStreetAddress("12 Main Street");
        profile.setWard("Ward 1");
        profile.setDistrict(" ");
        profile.setProvince("City");
        user.setProfile(profile);
        Post post = new Post();
        post.setUser(user);
        post.getImages().add(new PostImage("first.jpg", "fingerprint"));
        var mapper = context.getBean(PostMapper.class);

        var response = mapper.toResponse(post);
        assertEquals(user.getId(), response.user().id());
        assertEquals("12 Main Street, Ward 1, City", response.user().address());
        assertEquals("first.jpg", response.imageUrls().getFirst());
        post.getImages().clear();
        assertEquals("first.jpg", response.imageUrls().getFirst());
        assertEquals(response.user().address(), mapper.toMarketplaceResponse(post).sellerAddress());
    }

    @Test
    void inspectionNamesKeepTheirDistinctFallbackRules() {
        User buyer = user("buyer@example.test");
        buyer.setProfile(new UserProfile());
        User inspector = user("inspector@example.test");
        inspector.setProfile(new UserProfile());
        InspectionOrder order = new InspectionOrder();
        order.setBuyer(buyer);
        order.setInspector(inspector);
        order.setVideoCleanupStatus("CLEANED");
        order.setStatus("COMPLETED");
        order.setNote("Inspection note");

        var response = context.getBean(InspectionOrderMapper.class).toResponse(order);
        assertEquals("COMPLETED", response.status());
        assertEquals("Inspection note", response.note());
        assertEquals("CLEANED", response.videoCleanupStatus());
        assertNull(response.buyerName());
        assertNull(response.sellerName());
        assertEquals("inspector@example.test", response.inspectorName());
        assertTrue(response.videoCleanedMessage().contains("48h"));
    }

    @Test
    void verificationViewsKeepMaskedDocumentAndContactFallbacks() {
        User user = user("shop@example.test");
        UserProfile profile = new UserProfile();
        profile.setPhone("0912345678");
        user.setProfile(profile);
        SellerVerification verification = new SellerVerification();
        verification.setUser(user);
        verification.setDocumentNumberMasked("********1234");
        var mapper = context.getBean(SellerVerificationMapper.class);

        var response = mapper.toResponse(verification, "Shop", " ", " ", "Pickup", null);
        assertEquals("********1234", response.documentNumber());
        assertEquals("shop@example.test", response.shopEmail());
        assertEquals("0912345678", response.phone());
        assertTrue(response.eventHistory().isEmpty());
        assertTrue(mapper.toDetailResponse(verification, null).eventHistory().isEmpty());
        assertNull(mapper.toResponse(null));
    }

    @Test
    void withdrawalProjectionKeepsNullableRelations() {
        WalletWithdrawal withdrawal = new WalletWithdrawal();
        withdrawal.setId(UUID.randomUUID());
        var response = context.getBean(WalletMapper.class).toWithdrawalResponse(withdrawal);
        assertEquals(withdrawal.getId(), response.id());
        assertNull(response.userId());
        assertNull(response.userEmail());
        assertNull(response.reviewedBy());
    }

    private static User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        return user;
    }
}

package com.secondlife.secondlife.architecture;

import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.service.impl.AuthServiceImpl;
import com.secondlife.secondlife.service.impl.IdentityAccessServiceImpl;
import com.secondlife.secondlife.service.impl.InspectionOrderChecklistServiceImpl;
import com.secondlife.secondlife.service.impl.ListingAccessServiceImpl;
import com.secondlife.secondlife.service.impl.OrderConfirmationServiceImpl;
import com.secondlife.secondlife.service.impl.ShippingCatalogServiceImpl;
import com.secondlife.secondlife.service.impl.WalletQueryServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleBoundariesTest {
    private static final String ROOT = "com.secondlife.secondlife";

    @Test
    void businessServicesCommunicateThroughContracts() throws Exception {
        var violations = new ArrayList<String>();
        for (Class<?> type : businessServiceTypes()) {
            for (var field : type.getDeclaredFields()) {
                Class<?> dependency = field.getType();
                if (dependency.isAnnotationPresent(Service.class)
                        && Arrays.stream(dependency.getInterfaces())
                        .anyMatch(contract -> contract.getPackageName().equals(ROOT + ".service"))) {
                    violations.add(type.getSimpleName() + "." + field.getName()
                            + " injects business implementation " + dependency.getSimpleName());
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void representativeContextSeamsKeepApprovedRepositoryDependencies() {
        // Existing atomic workflow exceptions are explained in BACKEND_MODULE_BOUNDARIES.md.
        Map<Class<?>, Set<Class<?>>> allowed = Map.of(
                AuthServiceImpl.class, Set.of(UserRepository.class, RoleRepository.class),
                ListingAccessServiceImpl.class, Set.of(PostRepository.class, SellerVerificationRepository.class),
                OrderConfirmationServiceImpl.class, Set.of(OrderRepository.class, ShipmentRepository.class, PostRepository.class),
                InspectionOrderChecklistServiceImpl.class, Set.of(InspectionOrderRepository.class),
                ShippingCatalogServiceImpl.class, Set.of(),
                WalletQueryServiceImpl.class, Set.of(WalletTransactionRepository.class));
        var violations = new ArrayList<String>();
        allowed.forEach((service, repositories) -> {
            for (var field : service.getDeclaredFields()) {
                Class<?> dependency = field.getType();
                if (dependency.getName().startsWith(ROOT + ".repository.") && !repositories.contains(dependency)) {
                    violations.add(service.getSimpleName() + "." + field.getName()
                            + " crosses an unapproved persistence boundary: " + dependency.getSimpleName());
                }
            }
        });
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void listingAuthorizationUsesTheIdentityContract() {
        Set<String> services = Arrays.stream(ListingAccessServiceImpl.class.getDeclaredFields())
                .filter(field -> field.getType().getName().startsWith(ROOT + ".service."))
                .map(field -> field.getType().getSimpleName())
                .collect(Collectors.toSet());

        assertEquals(Set.of("IdentityAccessService", "SellerReputationService"), services);
    }

    @Test
    void identityRoleLookupDoesNotDependOnMarketplaceWorkflows() {
        Set<Class<?>> dependencies = Arrays.stream(IdentityAccessServiceImpl.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(field -> field.getType())
                .collect(Collectors.toSet());

        assertEquals(Set.of(UserRepository.class), dependencies);
    }

    private List<Class<?>> businessServiceTypes() throws IOException, ClassNotFoundException {
        Path classes = Path.of("target/classes");
        Path implementations = classes.resolve(ROOT.replace('.', '/')).resolve("service/impl");
        var result = new ArrayList<Class<?>>();
        try (var paths = Files.walk(implementations)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")
                    && !p.getFileName().toString().contains("$")).toList()) {
                String name = classes.relativize(path).toString().replace('\\', '.').replace('/', '.');
                Class<?> type = Class.forName(name.substring(0, name.length() - 6), false, getClass().getClassLoader());
                if (type.isAnnotationPresent(Service.class) && !Modifier.isAbstract(type.getModifiers())) {
                    result.add(type);
                }
            }
        }
        assertTrue(!result.isEmpty(), "No business services discovered; compile application classes before this test");
        return result;
    }
}

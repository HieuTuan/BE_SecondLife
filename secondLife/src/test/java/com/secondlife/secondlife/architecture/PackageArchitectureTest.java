package com.secondlife.secondlife.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageArchitectureTest {
    private static final String ROOT = "com.secondlife.secondlife";

    @Test
    void controllersDependOnServiceContractsRatherThanPersistenceOrImplementations() throws Exception {
        var violations = new ArrayList<String>();
        for (Class<?> type : applicationTypes()) {
            if (!type.isAnnotationPresent(RestController.class)) continue;
            for (var field : type.getDeclaredFields()) {
                String dependency = field.getType().getName();
                if (dependency.startsWith(ROOT + ".repository.")
                        || dependency.startsWith(ROOT + ".entity.")
                        || dependency.startsWith(ROOT + ".service.impl.")
                        || dependency.startsWith("jakarta.persistence.")) {
                    violations.add(type.getSimpleName() + "." + field.getName() + " -> " + dependency);
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void businessServicesExposeInterfacesAndKeepImplementationsUnderImpl() throws Exception {
        var violations = new ArrayList<String>();
        for (Class<?> type : applicationTypes()) {
            if (!type.getName().startsWith(ROOT + ".service.")
                    || !type.isAnnotationPresent(Service.class) || Modifier.isAbstract(type.getModifiers())) continue;
            if (!type.getName().startsWith(ROOT + ".service.impl.") || type.getInterfaces().length == 0) {
                violations.add(type.getName());
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void businessServicesDoNotOwnDatabaseOrVendorClients() throws Exception {
        var violations = new ArrayList<String>();
        for (Class<?> type : applicationTypes()) {
            if (!type.getName().startsWith(ROOT + ".service.")) continue;
            for (var field : type.getDeclaredFields()) {
                String dependency = field.getType().getName();
                if (dependency.startsWith("org.springframework.jdbc.")
                        || dependency.startsWith("jakarta.persistence.")
                        || dependency.startsWith("org.springframework.web.client.")
                        || dependency.startsWith("org.springframework.web.reactive.function.client.")
                        || dependency.startsWith("org.springframework.mail.")
                        || dependency.startsWith("org.springframework.ai.chat.model.")
                        || dependency.startsWith("com.cloudinary.")
                        || dependency.startsWith("com.google.api.client.")) {
                    violations.add(type.getSimpleName() + "." + field.getName() + " -> " + dependency);
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void repositoriesAreJpaInterfaces() throws Exception {
        var violations = new ArrayList<String>();
        var repositoriesByEntity = new HashMap<Class<?>, String>();
        for (Class<?> type : applicationTypes()) {
            if (!type.getName().startsWith(ROOT + ".repository.")) continue;
            if (!type.isInterface()
                    || type.getSimpleName().endsWith("Repository") && !JpaRepository.class.isAssignableFrom(type)) {
                violations.add(type.getName());
            }
            if (JpaRepository.class.isAssignableFrom(type)) {
                Class<?> entity = ResolvableType.forClass(type).as(JpaRepository.class).getGeneric(0).resolve();
                String previous = repositoriesByEntity.putIfAbsent(entity, type.getName());
                if (previous != null) violations.add(previous + " and " + type.getName() + " share " + entity);
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void apiDtosDoNotDependOnJpaEntities() throws Exception {
        var violations = new ArrayList<String>();
        for (Class<?> type : applicationTypes()) {
            if (!type.getName().startsWith(ROOT + ".dto.")) continue;
            for (var field : type.getDeclaredFields()) {
                if (field.getType().getName().startsWith(ROOT + ".entity.")) {
                    violations.add(type.getSimpleName() + "." + field.getName());
                }
            }
            for (var method : type.getDeclaredMethods()) {
                for (Class<?> parameter : method.getParameterTypes()) {
                    if (parameter.getName().startsWith(ROOT + ".entity.")) {
                        violations.add(type.getSimpleName() + "." + method.getName());
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    @Test
    void sourceTreeFollowsTheApprovedLayeredLayout() throws IOException {
        Path source = Path.of("src/main/java", ROOT.replace('.', '/'));
        var allowed = java.util.Set.of("controller", "dto", "entity", "repository", "mapper",
                "service", "integration", "scheduler", "seeder", "config", "security",
                "exception", "enums", "common");
        var violations = new ArrayList<String>();
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                Path relative = source.relativize(file);
                String layer = relative.getName(0).toString();
                if (relative.getNameCount() == 1 && layer.equals("SecondLifeApplication.java")) continue;
                boolean valid = allowed.contains(layer);
                if (layer.equals("dto")) {
                    valid &= relative.getNameCount() == 3
                            && java.util.Set.of("request", "response").contains(relative.getName(1).toString());
                } else if (layer.equals("service")) {
                    valid &= relative.getNameCount() == 2
                            ? Files.readString(file).matches("(?s).*public\\s+interface\\s+.*")
                            : relative.getNameCount() == 3 && relative.getName(1).toString().equals("impl");
                }
                if (layer.equals("repository")) {
                    valid &= relative.getNameCount() == 2
                            && Files.readString(file).matches("(?s).*public\\s+interface\\s+.*");
                }
                String expectedPackage = ROOT + "." + relative.getParent().toString().replace('\\', '.').replace('/', '.');
                valid &= Files.readString(file).contains("package " + expectedPackage + ";");
                if (!valid) violations.add(relative.toString());
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }

    private List<Class<?>> applicationTypes() throws IOException, ClassNotFoundException {
        Path classes = Path.of("target/classes");
        var result = new ArrayList<Class<?>>();
        try (var paths = Files.walk(classes.resolve(ROOT.replace('.', '/')))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")
                    && !p.getFileName().toString().contains("$")).toList()) {
                String name = classes.relativize(path).toString().replace('\\', '.').replace('/', '.');
                result.add(Class.forName(name.substring(0, name.length() - 6), false, getClass().getClassLoader()));
            }
        }
        return result;
    }
}

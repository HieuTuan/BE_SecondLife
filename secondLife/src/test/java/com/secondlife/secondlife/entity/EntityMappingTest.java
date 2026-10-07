package com.secondlife.secondlife.entity;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityMappingTest {
    @Test
    void allEntityMappingsBootWithScalarCoverImageAndOrderedProductImages() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.connection.provider_class",
                        "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var candidate : scanner.findCandidateComponents("com.secondlife.secondlife.entity")) {
                sources.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
            }
            try (var factory = sources.buildMetadata().getSessionFactoryBuilder().build()) {
                var post = factory.getMetamodel().entity(Post.class);
                assertEquals(String.class, post.getSingularAttribute("imageUrl", String.class).getJavaType());
                assertEquals(PostImage.class, post.getList("images", PostImage.class).getElementType().getJavaType());
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}

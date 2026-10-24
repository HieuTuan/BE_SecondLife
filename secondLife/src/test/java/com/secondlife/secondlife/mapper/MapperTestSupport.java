package com.secondlife.secondlife.mapper;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public final class MapperTestSupport {
    private static final AnnotationConfigApplicationContext CONTEXT =
            new AnnotationConfigApplicationContext("com.secondlife.secondlife.mapper");

    private MapperTestSupport() { }

    public static <T> T mapper(Class<T> type) {
        return CONTEXT.getBean(type);
    }
}

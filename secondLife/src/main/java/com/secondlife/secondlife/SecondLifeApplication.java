package com.secondlife.secondlife;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
public class SecondLifeApplication {

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(SecondLifeApplication.class, args);
    }

    private static void loadDotEnvIfPresent() {
        for (Path candidate : List.of(Path.of(".env"), Path.of("secondLife", ".env"))) {
            if (Files.isRegularFile(candidate)) {
                try {
                    for (String rawLine : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                        String line = rawLine.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eq = line.indexOf('=');
                        if (eq <= 0) {
                            continue;
                        }
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        if ("JAVA_TOOL_OPTIONS".equals(key)) {
                            continue;
                        }
                        if (System.getenv(key) == null && System.getProperty(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }

}

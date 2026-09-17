package com.secondlife.secondlife.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File envFile = findDotenvFile();
        if (envFile == null || !envFile.exists() || !envFile.isFile()) {
            return;
        }

        Map<String, Object> envMap = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }

                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();

                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    envMap.put(key, value);

                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Notice: Could not read .env file: " + e.getMessage());
        }

        if (!envMap.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, envMap));
        }
    }

    private File findDotenvFile() {
        Path[] searchPaths = new Path[]{
                Paths.get(".env"),
                Paths.get(System.getProperty("user.dir", "."), ".env"),
                Paths.get("..", ".env")
        };

        for (Path p : searchPaths) {
            File f = p.toFile();
            if (f.exists() && f.isFile()) {
                return f;
            }
        }
        return null;
    }
}

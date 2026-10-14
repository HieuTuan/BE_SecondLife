package com.secondlife.secondlife.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${app.firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${app.firebase.credentials-json:}")
    private String credentialsJson;

    @PostConstruct
    public void initializeFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("FirebaseApp already initialized");
            return;
        }

        try (InputStream serviceAccount = resolveServiceAccountStream()) {
            if (serviceAccount == null) {
                log.info("No Firebase credentials provided. FCM push notification will operate in mock/log mode.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            log.info("FirebaseApp successfully initialized for FCM push notifications");
        } catch (Exception e) {
            log.warn("Failed to initialize FirebaseApp: {}. FCM will operate in mock/log mode.", e.getMessage());
        }
    }

    private InputStream resolveServiceAccountStream() throws Exception {
        if (credentialsJson != null && !credentialsJson.isBlank()) {
            return new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8));
        }

        String envJson = System.getenv("FIREBASE_CREDENTIALS_JSON");
        if (envJson != null && !envJson.isBlank()) {
            return new ByteArrayInputStream(envJson.getBytes(StandardCharsets.UTF_8));
        }

        String path = (credentialsPath != null && !credentialsPath.isBlank())
                ? credentialsPath
                : System.getenv("FIREBASE_CREDENTIALS_PATH");

        if (path != null && !path.isBlank()) {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                return new FileInputStream(file);
            }
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is != null) {
                return is;
            }
            log.warn("Firebase credentials path [{}] specified but file does not exist", path);
        }

        return null;
    }
}

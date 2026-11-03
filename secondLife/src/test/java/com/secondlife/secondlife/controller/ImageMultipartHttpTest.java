package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.GlobalExceptionHandler;

import jakarta.servlet.MultipartConfigElement;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real servlet multipart parsing over loopback; no database, storage or authenticated user is used. */
class ImageMultipartHttpTest {
    @TempDir static Path temporary;
    private static Tomcat tomcat;
    private static URI endpoint;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws Exception {
        tomcat = new Tomcat();
        tomcat.setBaseDir(temporary.resolve("tomcat").toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("", temporary.toString());
        var spring = new AnnotationConfigWebApplicationContext();
        spring.register(WebConfiguration.class);
        var servlet = Tomcat.addServlet(context, "dispatcher", new DispatcherServlet(spring));
        servlet.setLoadOnStartup(1);
        // Deliberately small transport limits keep boundary tests fast.
        servlet.setMultipartConfigElement(new MultipartConfigElement(temporary.toString(), 1024, 2048, 0));
        context.addServletMappingDecoded("/", "dispatcher");
        tomcat.start();
        endpoint = URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/api/v1/posts/init");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (client != null) client.close();
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    @Test
    void validMultipartReachesTheControllerOverHttp() throws Exception {
        var response = sendMultipart(1, 16);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"accepted\":1"));
    }

    @Test
    void oversizedFileIs413JsonBeforeControllerInvocation() throws Exception {
        var response = sendMultipart(1, 1025);
        assertEquals(413, response.statusCode());
        assertTrue(response.body().contains("\"success\":false"));
        assertTrue(response.body().contains("\"field\":\"images\""));
    }

    @Test
    void totalMultipartLimitIs413EvenWhenEachFileIsSmall() throws Exception {
        var response = sendMultipart(8, 256);
        assertEquals(413, response.statusCode());
        assertTrue(response.body().contains("\"success\":false"));
    }

    private HttpResponse<String> sendMultipart(int count, int bytesPerFile) throws Exception {
        String boundary = "SecondLifeImageBoundary";
        var body = new ByteArrayOutputStream();
        for (int index = 0; index < count; index++) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"images\"; filename=\"image.jpg\"\r\n"
                    + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(new byte[bytesPerFile]);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    @EnableWebMvc
    static class WebConfiguration {
        @Bean UploadController uploadController() { return new UploadController(); }
        @Bean GlobalExceptionHandler globalExceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean StandardServletMultipartResolver multipartResolver() { return new StandardServletMultipartResolver(); }
    }

    @RestController
    @TestComponent
    static class UploadController {
        @PostMapping("/api/v1/posts/init")
        Map<String, Integer> upload(@RequestParam("images") List<MultipartFile> images) {
            return Map.of("accepted", images.size());
        }
    }
}

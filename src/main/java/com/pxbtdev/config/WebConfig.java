// ./src/main/java/com/pxbtdev/config/WebConfig.java (updated)
package com.pxbtdev.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:8080", "http://127.0.0.1:8080")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static files from classpath
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");

        // Serve Playwright reports from file system
        registry.addResourceHandler("/playwright-report/**")
                .addResourceLocations("file:./playwright-report/");

        // Serve test results from file system
        registry.addResourceHandler("/test-results/**")
                .addResourceLocations("file:./test-results/");

        // Serve generated test scripts
        registry.addResourceHandler("/generated-playwright-tests/**")
                .addResourceLocations("file:./generated-playwright-tests/");
    }
}
package com.pxbtdev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AutomationScriptCreatorApplication {

    private static final Logger log = LoggerFactory.getLogger(AutomationScriptCreatorApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AutomationScriptCreatorApplication.class, args);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Application shutting down...");
            cleanup();
        }));
    }

    private static void cleanup() {
        // Cleanup any resources
        log.info("Cleaning up resources...");

        try {
            // Force garbage collection
            System.gc();
            log.info("Garbage collection requested");
        } catch (Exception e) {
            log.error("Error during cleanup", e);
        }
    }
}
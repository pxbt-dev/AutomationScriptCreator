package com.pxbtdev.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.enhancement")
public class AIProperties {

    // Limits for different modes
    private int limitSingle = 1;    // Single AI test mode
    private int limitAll = 0;       // All AI tests mode (0 = NO LIMIT)
    private int limitFast = 0;      // Fast mode (no AI)
    private int limitPlaywright = 5; // Playwright generation mode

    // Safety limits to prevent overload
    private int maxTotalTestCases = 100;     // Maximum total test cases to generate
    private int maxAITestsPerRequest = 20;   // Maximum AI tests even if "unlimited"
    private int maxConcurrentAIRequests = 5; // Maximum concurrent AI requests

    // Timeouts
    private long timeoutMs = 30000; // 30 seconds per AI request
    private int batchSize = 5;      // Reduced from 10

    // Thresholds
    private int minElementsForAI = 3;
    private int maxElementsForAI = 50;

    // Quality settings
    private boolean enableAdvancedPrompting = true;
    private double confidenceThreshold = 0.7;

    public boolean isUnlimited(int limit) {
        return limit <= 0; // 0 or negative = NO LIMIT
    }

    public int getLimitForMode(String mode) {
        if (mode == null) {
            return limitFast;
        }

        switch (mode.toLowerCase()) {
            case "single":
                return limitSingle;
            case "all":
                return limitAll;
            case "fast":
                return limitFast;
            case "playwright":
                return limitPlaywright;
            default:
                return limitFast;
        }
    }

    public int getSafeAILimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            // If unlimited (0), use max safety limit
            return maxAITestsPerRequest;
        }
        return Math.min(requestedLimit, maxAITestsPerRequest);
    }
}
package com.pxbtdev.component;

import com.pxbtdev.service.PlaywrightMCPService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Lazy  // LAZY INITIALIZATION - No browser starts at app startup
public class BrowserDriver {

    private final PlaywrightMCPService playwrightService;
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    @Value("${automation.browser.headless:true}")
    private boolean defaultHeadlessMode;

    @Value("${automation.browser.lazy-init:true}")
    private boolean lazyInit;

    @Value("${automation.browser.max-sessions:5}")
    private int maxSessions;

    private volatile boolean initialized = false;

    public BrowserDriver(@Lazy PlaywrightMCPService playwrightService) {
        this.playwrightService = playwrightService;
        log.info("✅ BrowserDriver created (lazyInit={}, maxSessions={})", lazyInit, maxSessions);
    }

    @PostConstruct
    public void init() {
        if (!lazyInit) {
            log.info("⚠️ Initializing browser driver on startup (not recommended for production)");
            // Just log, don't actually initialize anything
        } else {
            log.info("✅ BrowserDriver will initialize on first use (lazy initialization)");
        }
    }

    private synchronized void ensureInitialized() {
        if (initialized) return;

        log.info("🔄 Initializing browser components on first use...");
        // The Playwright service will initialize when needed
        initialized = true;
        log.info("✅ Browser components ready (will launch only when needed)");
    }

    public void startRecording(String sessionId, String url, Boolean headlessMode) {
        ensureInitialized();

        log.info("🎬 Starting recording session: {} for URL: {}", sessionId, url);

        try {
            // Check for too many active sessions
            if (activeSessions.size() >= maxSessions) {
                throw new RuntimeException("Too many active sessions. Maximum is " + maxSessions + ".");
            }

            // Use Playwright MCP service
            boolean headless = headlessMode != null ? headlessMode : defaultHeadlessMode;
            playwrightService.createSession(sessionId, url, headless);

            activeSessions.put(sessionId, url);
            log.info("✅ Browser session started via Playwright MCP: {}", sessionId);

        } catch (Exception e) {
            log.error("❌ Failed to start browser session for URL: {}", url, e);
            throw new RuntimeException("Browser session failed: " + e.getMessage(), e);
        }
    }

    public void stopRecording(String sessionId) {
        if (activeSessions.containsKey(sessionId)) {
            try {
                playwrightService.closeSession(sessionId);
                activeSessions.remove(sessionId);
                log.info("⏹️ Browser session stopped: {}", sessionId);
            } catch (Exception e) {
                log.error("Error stopping browser session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    public String captureElementScreenshot(String sessionId, String selector) {
        if (activeSessions.containsKey(sessionId)) {
            return playwrightService.captureScreenshot(sessionId, selector);
        }
        return null;
    }

    public Map<String, Object> inspectElement(String sessionId, String selector) {
        if (activeSessions.containsKey(sessionId)) {
            return playwrightService.getElementInfo(sessionId, selector);
        }
        return Map.of();
    }
}
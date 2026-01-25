package com.pxbtdev.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Lazy  // LAZY INITIALIZATION - No Playwright starts at app startup
public class PlaywrightMCPService {

    private Playwright playwright;
    private Browser browser;
    private final Map<String, BrowserContext> sessions = new ConcurrentHashMap<>();
    private final Map<String, Page> pages = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${playwright.mcp.enabled:true}")
    private boolean mcpEnabled;

    @Value("${playwright.mcp.url:http://localhost:3000}")
    private String mcpUrl;

    @Value("${playwright.lazy-init:true}")
    private boolean lazyInit;

    @Value("${playwright.browser.headless:true}")
    private boolean browserHeadless;

    @Value("${playwright.browser.viewport.width:1920}")
    private int viewportWidth;

    @Value("${playwright.browser.viewport.height:1080}")
    private int viewportHeight;

    @Getter
    private volatile boolean initialized = false;
    @Getter
    private volatile boolean mcpAvailable = false;

    public PlaywrightMCPService() {
        log.info("✅ PlaywrightMCPService created (lazyInit={}, mcpEnabled={})", lazyInit, mcpEnabled);
    }

    private synchronized void ensureInitialized() {
        if (initialized) return;

        log.info("🔄 Initializing Playwright on first use...");

        try {
            if (mcpEnabled) {
                // Try to connect to MCP server
                log.info("Checking MCP server at: {}", mcpUrl);
                mcpAvailable = testMCPConnection();

                if (mcpAvailable) {
                    log.info("✅ Playwright MCP server detected and available");
                    initialized = true;
                    return;
                } else {
                    log.warn("⚠️ MCP server not available, falling back to embedded Playwright");
                }
            }

            // Fallback to embedded Playwright
            log.info("🚀 Starting embedded Playwright (headless={})", browserHeadless);
            playwright = Playwright.create();

            // Configure browser with optimal settings
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(browserHeadless)
                    .setArgs(Arrays.asList(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-gpu",
                            "--disable-setuid-sandbox",
                            "--disable-web-security",
                            "--disable-features=IsolateOrigins,site-per-process"
                    ))
                    .setTimeout(30000); // 30 second timeout

            browser = playwright.chromium().launch(options);

            initialized = true;
            log.info("✅ Embedded Playwright initialized successfully");

        } catch (Exception e) {
            log.error("❌ Failed to initialize Playwright", e);
            throw new RuntimeException("Playwright initialization failed", e);
        }
    }

    private boolean testMCPConnection() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mcpUrl + "/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean available = response.statusCode() == 200;

            if (available) {
                log.debug("MCP server health check passed");
            } else {
                log.debug("MCP server health check failed: HTTP {}", response.statusCode());
            }

            return available;

        } catch (Exception e) {
            log.debug("MCP server not available: {}", e.getMessage());
            return false;
        }
    }

    public String createSession(String sessionId, String url, boolean headless) {
        ensureInitialized(); // Initialize ONLY when needed

        try {
            if (mcpEnabled && mcpAvailable) {
                // Use MCP server
                return createSessionViaMCP(sessionId, url, headless);
            } else {
                // Use embedded Playwright
                return createSessionEmbedded(sessionId, url, headless);
            }
        } catch (Exception e) {
            log.error("❌ Failed to create session {}", sessionId, e);
            throw new RuntimeException("Failed to create browser session", e);
        }
    }

    private String createSessionViaMCP(String sessionId, String url, boolean headless) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "jsonrpc", "2.0",
                "method", "create_session",
                "params", Map.of(
                        "sessionId", sessionId,
                        "url", url,
                        "headless", headless,
                        "viewport", Map.of("width", viewportWidth, "height", viewportHeight)
                ),
                "id", 1
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mcpUrl + "/rpc"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
            if (responseBody.get("error") == null) {
                log.info("✅ MCP session created: {}", sessionId);
                return sessionId;
            }
        }

        throw new RuntimeException("MCP session creation failed");
    }

    private String createSessionEmbedded(String sessionId, String url, boolean headless) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(viewportWidth, viewportHeight));

        Page page = context.newPage();

        // Set longer timeouts
        page.setDefaultTimeout(30000); // 30 seconds
        page.setDefaultNavigationTimeout(60000); // 60 seconds

        log.info("🌐 Navigating to: {}", url);
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

        // Wait for page to be ready
        page.waitForLoadState(LoadState.NETWORKIDLE);

        sessions.put(sessionId, context);
        pages.put(sessionId, page);

        log.info("✅ Embedded session created: {} (headless={})", sessionId, headless);
        return sessionId;
    }

    public String captureScreenshot(String sessionId, String selector) {
        ensureInitialized();

        try {
            if (pages.containsKey(sessionId)) {
                // Embedded mode
                Page page = pages.get(sessionId);
                byte[] screenshot;

                if (selector != null && !selector.isEmpty()) {
                    screenshot = page.locator(selector).screenshot();
                } else {
                    screenshot = page.screenshot(new Page.ScreenshotOptions()
                            .setFullPage(true));
                }

                // Save to file
                String filename = "screenshot_" + sessionId + "_" + System.currentTimeMillis() + ".png";
                Path filePath = Path.of("screenshots", filename);
                java.nio.file.Files.createDirectories(filePath.getParent());
                java.nio.file.Files.write(filePath, screenshot);

                log.info("📸 Screenshot saved: {}", filename);
                return filename;
            } else if (mcpEnabled && mcpAvailable) {
                // MCP mode
                return captureScreenshotViaMCP(sessionId, selector);
            }
        } catch (Exception e) {
            log.error("❌ Failed to capture screenshot for session {}", sessionId, e);
        }
        return null;
    }

    private String captureScreenshotViaMCP(String sessionId, String selector) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "jsonrpc", "2.0",
                "method", "capture_screenshot",
                "params", Map.of(
                        "sessionId", sessionId,
                        "selector", selector != null ? selector : "",
                        "fullPage", selector == null
                ),
                "id", 2
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mcpUrl + "/rpc"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
            return (String) ((Map<String, Object>) responseBody.get("result")).get("filename");
        }

        return null;
    }

    public Map<String, Object> getElementInfo(String sessionId, String selector) {
        ensureInitialized();

        try {
            if (pages.containsKey(sessionId)) {
                // Embedded mode
                Page page = pages.get(sessionId);
                ElementHandle element = page.querySelector(selector);

                if (element != null) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("tagName", element.evaluate("el => el.tagName"));
                    info.put("text", element.evaluate("el => el.textContent"));
                    info.put("attributes", element.evaluate("el => { const attrs = {}; for (const attr of el.attributes) { attrs[attr.name] = attr.value; } return attrs; }"));
                    info.put("bounds", element.boundingBox());
                    info.put("visible", element.isVisible());
                    info.put("enabled", element.isEnabled());
                    info.put("checked", element.isChecked());

                    return info;
                }
            } else if (mcpEnabled && mcpAvailable) {
                // MCP mode
                return getElementInfoViaMCP(sessionId, selector);
            }
        } catch (Exception e) {
            log.error("❌ Failed to get element info for session {}", sessionId, e);
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> getElementInfoViaMCP(String sessionId, String selector) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "jsonrpc", "2.0",
                "method", "get_element_info",
                "params", Map.of(
                        "sessionId", sessionId,
                        "selector", selector
                ),
                "id", 3
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mcpUrl + "/rpc"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Map<String, Object> responseBody = objectMapper.readValue(response.body(), Map.class);
            return (Map<String, Object>) responseBody.get("result");
        }

        return Collections.emptyMap();
    }

    public void closeSession(String sessionId) {
        if (!initialized) {
            log.debug("Not initialized, nothing to close for session: {}", sessionId);
            return;
        }

        try {
            if (sessions.containsKey(sessionId)) {
                // Embedded mode
                BrowserContext context = sessions.remove(sessionId);
                Page page = pages.remove(sessionId);

                if (page != null) {
                    page.close();
                }
                if (context != null) {
                    context.close();
                }

                log.info("⏹️ Session closed: {}", sessionId);
            } else if (mcpEnabled && mcpAvailable) {
                // MCP mode
                closeSessionViaMCP(sessionId);
            }
        } catch (Exception e) {
            log.error("Error closing session {}", sessionId, e);
        }
    }

    private void closeSessionViaMCP(String sessionId) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "jsonrpc", "2.0",
                "method", "close_session",
                "params", Map.of("sessionId", sessionId),
                "id", 4
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mcpUrl + "/rpc"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public void shutdown() {
        if (!initialized) {
            log.info("Playwright service was never initialized, nothing to shutdown");
            return;
        }

        log.info("🛑 Shutting down Playwright service...");

        // Close all sessions
        new ArrayList<>(sessions.keySet()).forEach(this::closeSession);

        if (browser != null) {
            browser.close();
            log.debug("Browser closed");
        }
        if (playwright != null) {
            playwright.close();
            log.debug("Playwright closed");
        }

        initialized = false;
        log.info("✅ Playwright service shutdown complete");
    }

}
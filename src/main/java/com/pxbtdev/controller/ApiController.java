package com.pxbtdev.controller;

import com.pxbtdev.model.dto.ScriptRequest;
import com.pxbtdev.model.dto.ScriptResponse;
import com.pxbtdev.model.dto.RecordingRequest;
import com.pxbtdev.model.entity.RecordingSession;
import com.pxbtdev.model.entity.TestCase;
import com.pxbtdev.service.AITestEnhancerService;
import com.pxbtdev.service.RecordingService;
import com.pxbtdev.service.ScriptGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApiController {

    private final AITestEnhancerService aiTestEnhancerService;
    private final RecordingService recordingService;
    private final ScriptGenerationService scriptGenerationService;

    // Recording endpoints
    @PostMapping("/recordings")
    public ResponseEntity<Map<String, String>> startRecording(@RequestBody RecordingRequest request) {
        String sessionId = recordingService.startRecording(request);
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    @DeleteMapping("/recordings/{sessionId}/stop")
    public ResponseEntity<Void> stopRecording(@PathVariable String sessionId) {
        recordingService.stopRecording(sessionId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/recordings/{sessionId}")
    public ResponseEntity<Void> deleteRecording(@PathVariable String sessionId) {
        recordingService.stopRecording(sessionId);
        recordingService.deleteRecording(sessionId);
        return ResponseEntity.ok().build();
    }

    @Profile("dev")  // Only available in dev profile
    @PostMapping("/recordings/{sessionId}/test-actions")
    public ResponseEntity<Void> addTestActions(@PathVariable String sessionId) {
        recordingService.addTestActions(sessionId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recordings/{sessionId}")
    public ResponseEntity<RecordingSession> getRecording(@PathVariable String sessionId) {
        RecordingSession session = recordingService.getRecordingById(sessionId);
        if (session != null) {
            return ResponseEntity.ok(session);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/recordings/{sessionId}/events")
    public ResponseEntity<List<String>> getRecordingEvents(@PathVariable String sessionId) {
        List<String> events = recordingService.getRecordingEvents(sessionId);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/recordings")
    public ResponseEntity<List<String>> getAllRecordings() {
        List<String> recordings = recordingService.getAllRecordingSessions();
        return ResponseEntity.ok(recordings);
    }

    // Script generation endpoints
    @PostMapping("/scripts/generate")
    public ResponseEntity<ScriptResponse> generateScript(@RequestBody ScriptRequest request) {
        try {
            ScriptResponse response = scriptGenerationService.generateScript(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ScriptResponse("Error: " + e.getMessage(), "error.js",
                            System.currentTimeMillis(), 0)
            );
        }
    }

    @GetMapping("/scripts/{filename}")
    public ResponseEntity<String> getScript(@PathVariable String filename) {
        String script = scriptGenerationService.getScriptContent(filename);
        return ResponseEntity.ok(script);
    }

    // Health and info endpoints
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "AutomationScriptCreator");
        health.put("timestamp", System.currentTimeMillis());
        health.put("activeSessions", recordingService.getAllRecordingSessions().size());
        health.put("version", "1.0.0");
        return ResponseEntity.ok(health);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> getInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("name", "AutomationScriptCreator");
        info.put("description", "Web automation script generator");
        info.put("author", "pxbt-dev");
        info.put("javaVersion", System.getProperty("java.version"));
        return ResponseEntity.ok(info);
    }

    @PostMapping("/ai/enhance-test")
    public ResponseEntity<TestCase> enhanceTestWithAI(@RequestBody TestCase testCase) {
        try {
            String sampleHtml = "<html><body><h1>Test Page</h1></body></html>";
            TestCase enhanced = aiTestEnhancerService.enhanceWithAI(testCase, sampleHtml);
            return ResponseEntity.ok(enhanced);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/memory")
    public ResponseEntity<Map<String, Object>> getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();

        Map<String, Object> memoryInfo = new HashMap<>();
        memoryInfo.put("maxMemory", runtime.maxMemory() / (1024 * 1024) + " MB");
        memoryInfo.put("totalMemory", runtime.totalMemory() / (1024 * 1024) + " MB");
        memoryInfo.put("freeMemory", runtime.freeMemory() / (1024 * 1024) + " MB");
        memoryInfo.put("usedMemory", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB");
        memoryInfo.put("availableProcessors", runtime.availableProcessors());

        return ResponseEntity.ok(memoryInfo);
    }
}
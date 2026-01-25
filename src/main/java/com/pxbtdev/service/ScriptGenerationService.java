package com.pxbtdev.service;

import com.pxbtdev.model.dto.ScriptRequest;
import com.pxbtdev.model.dto.ScriptResponse;
import com.pxbtdev.model.entity.RecordingSession;
import com.pxbtdev.component.PlaywrightGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptGenerationService {

    private final RecordingService recordingService;
    private final PlaywrightGenerator playwrightGenerator;

    public ScriptResponse generateScript(ScriptRequest request) {
        RecordingSession session = recordingService.getRecordingById(request.getSessionId());

        if (session == null) {
            throw new RuntimeException("Session not found: " + request.getSessionId());
        }

        // Generate script content
        String scriptContent = playwrightGenerator.generateScript(
                session.getActions(),
                request.isIncludeAssertions(),
                request.isAddComments()
        );

        // Create filename
        String filename = createFilename(session.getSessionName());

        // Save to file
        saveScriptToFile(filename, scriptContent);

        // Create response - ALL these methods exist now!
        return new ScriptResponse(
                scriptContent,
                filename,
                Instant.now().toEpochMilli(),  // timestamp
                session.getActionCount()        // actionCount
        );
    }

    public String getScriptContent(String filename) {
        try {
            Path filePath = Paths.get("generated-scripts", filename);
            if (Files.exists(filePath)) {
                return Files.readString(filePath);
            }
            throw new RuntimeException("Script not found: " + filename);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read script: " + filename, e);
        }
    }

    private String createFilename(String sessionName) {
        String safeName = sessionName.replaceAll("[^a-zA-Z0-9]", "-");
        return String.format("%s-%s.js",
                safeName.toLowerCase(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    private void saveScriptToFile(String filename, String content) {
        try {
            Path outputDir = Paths.get("generated-scripts");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            Path filePath = outputDir.resolve(filename);
            Files.writeString(filePath, content);
            log.info("Script saved to: {}", filePath);
        } catch (Exception e) {
            log.error("Failed to save script: {}", filename, e);
            throw new RuntimeException("Failed to save script", e);
        }
    }
}
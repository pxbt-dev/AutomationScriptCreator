package com.pxbtdev.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pxbtdev.component.BrowserDriver;
import com.pxbtdev.model.dto.RecordingRequest;
import com.pxbtdev.model.entity.RecordingSession;
import com.pxbtdev.model.entity.UserAction;
import com.pxbtdev.model.enums.ActionType;
import com.pxbtdev.repository.RecordingSessionRepository;
import com.pxbtdev.repository.ActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingService {

    private final ObjectMapper objectMapper;
    private final BrowserDriver browserDriver;
    private final RecordingSessionRepository recordingSessionRepository;
    private final ActionRepository actionRepository;

    // Event storage (could also be moved to repository)
    private final Map<String, List<String>> recordingEvents = new ConcurrentHashMap<>();

    public String startRecording(RecordingRequest request) {
        String sessionId = UUID.randomUUID().toString();

        RecordingSession session = RecordingSession.builder()
                .id(sessionId)
                .sessionName(request.getSessionName())
                .url(request.getUrl())
                .startTime(LocalDateTime.now())
                .actions(new ArrayList<>())
                .build();

        // Save to repository
        recordingSessionRepository.save(session);
        recordingEvents.put(sessionId, new ArrayList<>());

        // Start browser session
        try {
            browserDriver.startRecording(sessionId, request.getUrl(), request.getHeadless());
            addRecordingEvent(sessionId, "Recording started at " + LocalDateTime.now());
            log.info("Recording started: {} for URL: {}", sessionId, request.getUrl());
        } catch (Exception e) {
            recordingSessionRepository.deleteById(sessionId);
            recordingEvents.remove(sessionId);
            log.error("Failed to start recording session: {}", sessionId, e);
            throw new RuntimeException("Failed to start browser session: " + e.getMessage(), e);
        }

        return sessionId;
    }

     public void stopRecording(String sessionId) {
        Optional<RecordingSession> optionalSession = recordingSessionRepository.findById(sessionId);
        if (optionalSession.isPresent()) {
            RecordingSession session = optionalSession.get();
            session.setEndTime(LocalDateTime.now());
            recordingSessionRepository.save(session);

            browserDriver.stopRecording(sessionId);
            addRecordingEvent(sessionId, "Recording stopped at " + LocalDateTime.now());
            log.info("Recording stopped: {}", sessionId);
        }
    }


    public void addAction(String sessionId, UserAction action) {
        Optional<RecordingSession> optionalSession = recordingSessionRepository.findById(sessionId);
        if (optionalSession.isPresent() && action != null) {
            if (action.getTimestamp() == null) {
                action.setTimestamp(LocalDateTime.now());
            }

            // Save action to repository
            actionRepository.save(action);

            // Update session (if actions are stored separately)
            RecordingSession session = optionalSession.get();
            session.addAction(action);
            recordingSessionRepository.save(session);

            addRecordingEvent(sessionId,
                    String.format("Action: %s on %s", action.getType(), action.getSelector()));

            log.debug("Action added to session {}: {}", sessionId, action.getType());
        }
    }

    public RecordingSession getSession(String sessionId) {
        return recordingSessionRepository.findById(sessionId).orElse(null);
    }

    public List<String> getAllRecordingSessions() {
        return recordingSessionRepository.findAll().stream()
                .map(RecordingSession::getId)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public List<String> getRecordingEvents(String sessionId) {
        return recordingEvents.getOrDefault(sessionId, Collections.emptyList());
    }

    public RecordingSession getRecordingById(String id) {
        return recordingSessionRepository.findById(id).orElse(null);
    }

    public void deleteRecording(String sessionId) {
        stopRecording(sessionId);
        recordingSessionRepository.deleteById(sessionId);
        actionRepository.deleteAllBySessionId(sessionId);
        recordingEvents.remove(sessionId);
        log.info("Recording deleted: {}", sessionId);
    }

    public void addTestActions(String sessionId) {
        Optional<RecordingSession> optionalSession = recordingSessionRepository.findById(sessionId);
        if (optionalSession.isPresent()) {
            RecordingSession session = optionalSession.get();

            // Click action
            UserAction clickAction = new UserAction();
            clickAction.setId(UUID.randomUUID().toString());
            clickAction.setType(ActionType.CLICK);
            clickAction.setSelector("button.submit");
            clickAction.setUrl(session.getUrl());
            clickAction.setTimestamp(LocalDateTime.now());

            // Type action
            UserAction typeAction = new UserAction();
            typeAction.setId(UUID.randomUUID().toString());
            typeAction.setType(ActionType.TYPE);
            typeAction.setSelector("input.email");
            typeAction.setValue("test@example.com");
            typeAction.setUrl(session.getUrl());
            typeAction.setTimestamp(LocalDateTime.now());

            actionRepository.save(clickAction);
            actionRepository.save(typeAction);

            session.addAction(clickAction);
            session.addAction(typeAction);
            recordingSessionRepository.save(session);

            addRecordingEvent(sessionId, "Test actions added to session");
            log.info("Added test actions to session: {}", sessionId);
        }
    }

    private void addRecordingEvent(String sessionId, String event) {
        List<String> events = recordingEvents.get(sessionId);
        if (events != null) {
            events.add(LocalDateTime.now() + ": " + event);
        }
    }

    public boolean isSessionActive(String sessionId) {
        return recordingSessionRepository.existsById(sessionId);
    }
}
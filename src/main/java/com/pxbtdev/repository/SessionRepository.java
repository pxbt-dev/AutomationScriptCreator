package com.pxbtdev.repository;

import com.pxbtdev.model.entity.RecordingSession;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class SessionRepository {

    private final ConcurrentHashMap<String, RecordingSession> sessions = new ConcurrentHashMap<>();

    public RecordingSession save(RecordingSession session) {
        if (session.getId() == null) {
            // Generate ID if not present (though RecordingService should handle this)
            session.setId(java.util.UUID.randomUUID().toString());
        }
        sessions.put(session.getId(), session);
        return session;
    }

    public Optional<RecordingSession> findById(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public List<RecordingSession> findAll() {
        return new ArrayList<>(sessions.values());
    }

    public List<RecordingSession> findBySessionName(String sessionName) {
        return sessions.values().stream()
                .filter(session -> sessionName.equalsIgnoreCase(session.getSessionName()))
                .collect(Collectors.toList());
    }

    public List<RecordingSession> findBySessionNameContaining(String keyword) {
        return sessions.values().stream()
                .filter(session -> session.getSessionName() != null &&
                        session.getSessionName().toLowerCase().contains(keyword.toLowerCase()))
                .collect(Collectors.toList());
    }

    public void deleteById(String id) {
        sessions.remove(id);
    }

    public boolean existsById(String id) {
        return sessions.containsKey(id);
    }

    public long count() {
        return sessions.size();
    }

    public void deleteAll() {
        sessions.clear();
    }

    public List<RecordingSession> findActiveSessions() {
        return sessions.values().stream()
                .filter(session -> session.getEndTime() == null) // Sessions without end time are active
                .collect(Collectors.toList());
    }

    public List<RecordingSession> findCompletedSessions() {
        return sessions.values().stream()
                .filter(session -> session.getEndTime() != null) // Sessions with end time are completed
                .collect(Collectors.toList());
    }
}
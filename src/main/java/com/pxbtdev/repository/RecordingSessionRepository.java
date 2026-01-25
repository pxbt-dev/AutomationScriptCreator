package com.pxbtdev.repository;

import com.pxbtdev.model.entity.RecordingSession;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class RecordingSessionRepository {

    private final Map<String, RecordingSession> storage = new ConcurrentHashMap<>();

    public RecordingSession save(RecordingSession session) {
        if (session.getId() == null) {
            session.setId(UUID.randomUUID().toString());
        }
        storage.put(session.getId(), session);
        return session;
    }

    public Optional<RecordingSession> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    public List<RecordingSession> findAll() {
        return new ArrayList<>(storage.values());
    }

    public void deleteById(String id) {
        storage.remove(id);
    }

    public boolean existsById(String id) {
        return storage.containsKey(id);
    }

    public List<RecordingSession> findBySessionNameContaining(String name) {
        return storage.values().stream()
                .filter(session -> session.getSessionName() != null &&
                        session.getSessionName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());
    }

    public long count() {
        return storage.size();
    }

    public void deleteAll() {
        storage.clear();
    }
}
package com.pxbtdev.repository;

import com.pxbtdev.model.entity.UserAction;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class ActionRepository {

    private final Map<String, UserAction> storage = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sessionActions = new ConcurrentHashMap<>();

    public UserAction save(UserAction action) {
        if (action.getId() == null) {
            action.setId(UUID.randomUUID().toString());
        }
        storage.put(action.getId(), action);
        return action;
    }

    public Optional<UserAction> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    public List<UserAction> findAllBySessionId(String sessionId) {
        // Simple implementation - you might need to adjust
        return storage.values().stream()
                .filter(action -> sessionId.equals(extractSessionId(action)))
                .collect(Collectors.toList());
    }

    private String extractSessionId(UserAction action) {
        // Implement based on your data structure
        return action.getUrl(); // Simple placeholder
    }

    public void deleteById(String id) {
        storage.remove(id);
    }

    public void deleteAllBySessionId(String sessionId) {
        List<UserAction> actions = findAllBySessionId(sessionId);
        actions.forEach(action -> storage.remove(action.getId()));
    }
}
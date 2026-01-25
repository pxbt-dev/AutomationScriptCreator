package com.pxbtdev.repository;

import com.pxbtdev.model.entity.UserAction;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class ActionRepositoryImpl extends ActionRepository {

    private final Map<String, UserAction> storage = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sessionActions = new ConcurrentHashMap<>();

    @Override
    public UserAction save(UserAction action) {
        if (action.getId() == null) {
            action.setId(UUID.randomUUID().toString());
        }
        storage.put(action.getId(), action);

        // Track by session
        String sessionId = extractSessionId(action);
        if (sessionId != null) {
            sessionActions.computeIfAbsent(sessionId, k -> new ArrayList<>())
                    .add(action.getId());
        }

        return action;
    }

    @Override
    public Optional<UserAction> findById(String id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<UserAction> findAllBySessionId(String sessionId) {
        List<String> actionIds = sessionActions.getOrDefault(sessionId, Collections.emptyList());
        return actionIds.stream()
                .map(storage::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<UserAction> findAllByType(String actionType) {
        return storage.values().stream()
                .filter(action -> action.getType() != null &&
                        action.getType().name().equalsIgnoreCase(actionType))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        UserAction action = storage.remove(id);
        if (action != null) {
            String sessionId = extractSessionId(action);
            if (sessionId != null) {
                List<String> actions = sessionActions.get(sessionId);
                if (actions != null) {
                    actions.remove(id);
                }
            }
        }
    }

    @Override
    public void deleteAllBySessionId(String sessionId) {
        List<String> actionIds = sessionActions.getOrDefault(sessionId, Collections.emptyList());
        actionIds.forEach(storage::remove);
        sessionActions.remove(sessionId);
    }

    public long countBySessionId(String sessionId) {
        return sessionActions.getOrDefault(sessionId, Collections.emptyList()).size();
    }

    private String extractSessionId(UserAction action) {
        // Extract session ID from URL or another field
        // This is a simplified implementation - you might need a different approach
        if (action.getUrl() != null && action.getUrl().contains("sessionId=")) {
            return action.getUrl().split("sessionId=")[1];
        }
        return null;
    }
}
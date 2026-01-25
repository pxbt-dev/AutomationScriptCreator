package com.pxbtdev.entity;

import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordingSession {
    private String id;
    private String sessionName;
    private String url;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @Builder.Default
    private List<UserAction> actions = new ArrayList<>();

    public void addAction(UserAction action) {
        this.actions.add(action);
    }

    public int getActionCount() {
        return this.actions.size();
    }
}
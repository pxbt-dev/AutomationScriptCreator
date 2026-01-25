package com.pxbtdev.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptResponse {
    private String script;
    private String filename;
    private long timestamp;  // This exists!
    private int actionCount; // This exists!

    // Convenience constructor
    public ScriptResponse(String script, String filename) {
        this.script = script;
        this.filename = filename;
        this.timestamp = System.currentTimeMillis();
        this.actionCount = 0;
    }
}
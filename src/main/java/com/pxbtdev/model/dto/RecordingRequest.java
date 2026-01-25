package com.pxbtdev.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RecordingRequest {
    private String url;
    private String sessionName;
    private Boolean headless;  // Changed from boolean to Boolean for null check
}
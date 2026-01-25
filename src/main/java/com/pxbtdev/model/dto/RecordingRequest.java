package com.pxbtdev.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RecordingRequest {
    private String url;
    private String sessionName;
    private boolean headless = true;
}
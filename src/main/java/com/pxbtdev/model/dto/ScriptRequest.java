package com.pxbtdev.model.dto;

import com.pxbtdev.model.enums.ScriptType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ScriptRequest {
    private String sessionId;
    private ScriptType scriptType = ScriptType.PLAYWRIGHT;
    private boolean includeAssertions = true;
    private boolean addComments = true;
}
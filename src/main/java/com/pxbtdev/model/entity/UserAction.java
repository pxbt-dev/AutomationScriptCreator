package com.pxbtdev.model.entity;

import com.pxbtdev.model.enums.ActionType;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserAction {
    private String id;
    private ActionType type;
    private String selector;
    private String value;
    private String url;
    private int x;
    private int y;
    private LocalDateTime timestamp;
}
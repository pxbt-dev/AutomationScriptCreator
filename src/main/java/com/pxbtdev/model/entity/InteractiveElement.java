package com.pxbtdev.model.entity;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InteractiveElement {

    private String selector;
    private String elementType; // button, input, link, select, textarea, form, nav, etc.
    private String actionType; // click, type, select, navigate, submit
    private String label; // text content or label
    private String placeholder;
    private String inputType; // for inputs: text, email, password, etc.
    private int priority; // 0-100 scoring
    private boolean required;
    private String tagName;
    private String id;
    private String name;
    private String href;
    private boolean visible;
}
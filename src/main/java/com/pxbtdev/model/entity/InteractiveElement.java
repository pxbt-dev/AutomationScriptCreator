package com.pxbtdev.model.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InteractiveElement {
    private String actionType;  // CLICK, TYPE, SELECT, NAVIGATE
    private String elementType; // Button, Link, Input, Dropdown
    private String selector;    // CSS selector
    private String text;        // Element text
    private String tagName;     // button, input, a, etc.
    private String id;          // HTML id
    private String classes;     // CSS classes
    private String testValue;   // Test data for inputs
    private int priority;       // Importance score
    private String description; // AI-generated description

    public String toTestStep() {
        switch (actionType) {
            case "CLICK":
                return String.format("Click on the '%s' (%s)", elementType, text);
            case "TYPE":
                return String.format("Enter '%s' into the %s field", testValue, elementType);
            case "SELECT":
                return String.format("Select an option from the %s dropdown", elementType);
            case "NAVIGATE":
                return String.format("Navigate using the %s", elementType);
            default:
                return String.format("Interact with the %s", elementType);
        }
    }

    public String toPlaywrightCode() {
        switch (actionType) {
            case "CLICK":
                return String.format("await page.click('%s');", selector);
            case "TYPE":
                return String.format("await page.fill('%s', '%s');", selector, testValue);
            case "SELECT":
                return String.format("await page.selectOption('%s', 'option-value');", selector);
            case "NAVIGATE":
                return String.format("// Navigation via: %s", selector);
            default:
                return String.format("// %s action on %s", actionType, selector);
        }
    }
}
package com.pxbtdev.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "automation")
public class AutomationProperties {

    private Browser browser = new Browser();
    private Script script = new Script();
    private Recordings recordings = new Recordings();

    @Data
    public static class Browser {
        private boolean headless = false;
        private int timeout = 30;
    }

    @Data
    public static class Script {
        private String outputDir = "./generated-scripts";
    }

    @Data
    public static class Recordings {
        private String dir = "./recordings";
    }
}
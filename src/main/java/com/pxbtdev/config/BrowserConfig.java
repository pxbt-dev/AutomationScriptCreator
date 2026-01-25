package com.pxbtdev.config;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
public class BrowserConfig {

    @Value("${webdriver.chrome.driver:/usr/local/bin/chromedriver}")
    private String chromeDriverPath;

    @Value("${automation.browser.headless:false}")
    private boolean headlessMode;

    @Value("${automation.browser.timeout:30}")
    private int timeoutSeconds;

    @Bean
    @Primary
    public WebDriver chromeDriver() {
        System.setProperty("webdriver.chrome.driver", chromeDriverPath);

        ChromeOptions options = new ChromeOptions();
        if (headlessMode) {
            options.addArguments("--headless");
        }
        options.addArguments("--start-maximized");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");

        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(timeoutSeconds));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(timeoutSeconds));

        return driver;
    }
}
package com.pxbtdev.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebPageAnalyzerService {

    private final RestTemplate restTemplate;

    public String fetchPageHtml(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            }
            log.error("Failed to fetch page: {}, Status: {}", url, response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("Error fetching page: {}", url, e);
            return null;
        }
    }
}
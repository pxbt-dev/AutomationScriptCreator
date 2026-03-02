package com.pxbtdev.service;

import com.pxbtdev.model.entity.InteractiveElement;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Fetches and analyses web pages using Jsoup.
 * Extracts page metadata and all interactive elements.
 */
@Slf4j
@Service
public class WebPageAnalyzerService {

    private static final int CONNECT_TIMEOUT_MS = 15000;

    /**
     * Fetch raw HTML from a URL.
     */
    public String fetchPageHtml(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .timeout(CONNECT_TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();
            return doc.outerHtml();
        } catch (IOException e) {
            log.error("Failed to fetch URL {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Full page analysis: metadata + element breakdown.
     */
    public Map<String, Object> analyzePage(String url) {
        Map<String, Object> result = new HashMap<>();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(CONNECT_TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();

            result.put("url", url);
            result.put("title", doc.title());
            result.put("htmlLength", doc.outerHtml().length());

            // Meta info
            String description = doc.select("meta[name=description]").attr("content");
            result.put("metaDescription", description);

            // Count element types
            result.put("formCount", doc.select("form").size());
            result.put("linkCount", doc.select("a[href]").size());
            result.put("buttonCount", doc.select("button, input[type=submit], input[type=button]").size());
            result.put("inputCount",
                    doc.select("input:not([type=hidden]):not([type=submit]):not([type=button])").size());
            result.put("selectCount", doc.select("select").size());
            result.put("textareaCount", doc.select("textarea").size());
            result.put("imageCount", doc.select("img").size());
            result.put("headingCount", doc.select("h1,h2,h3,h4,h5,h6").size());

            // Navigation structure
            Elements navLinks = doc.select("nav a, header a, [role=navigation] a");
            List<String> navItems = new ArrayList<>();
            for (Element a : navLinks) {
                String text = a.text().trim();
                if (!text.isEmpty())
                    navItems.add(text);
            }
            result.put("navigationItems", navItems);

            return result;

        } catch (IOException e) {
            log.error("Page analysis failed for {}: {}", url, e.getMessage());
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Parse HTML into a Jsoup Document.
     */
    public Document parseHtml(String html) {
        return Jsoup.parse(html);
    }
}
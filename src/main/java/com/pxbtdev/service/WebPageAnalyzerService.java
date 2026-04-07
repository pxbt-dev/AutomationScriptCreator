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
        log.info("Starting HTML fetch for URL: [{}]", url);
        long start = System.currentTimeMillis();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
                    .timeout(CONNECT_TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .get();
            String html = doc.outerHtml();
            log.info("Successfully fetched {} bytes from {} in {}ms", 
                    html.length(), url, (System.currentTimeMillis() - start));
            return html;
        } catch (IOException e) {
            log.error("Failed to fetch URL {} after {}ms: {}", url, (System.currentTimeMillis() - start), e.getMessage());
            return null;
        }
    }

    /**
     * Page analysis from already fetched HTML.
     */
    public Map<String, Object> analyzePage(String html, String url) {
        log.info("Starting page analysis for URL: [{}] (HTML length: {} bytes)", url, html.length());
        long start = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        try {
            Document doc = Jsoup.parse(html, url);

            String title = doc.title();
            log.debug("Extracted page title: [{}]", title);
            
            result.put("url", url);
            result.put("title", title);
            result.put("htmlLength", html.length());

            // Meta info
            String description = doc.select("meta[name=description]").attr("content");
            log.debug("Extracted meta description: [{}]", description);
            result.put("metaDescription", description);

            // Count element types
            int formCount = doc.select("form").size();
            int linkCount = doc.select("a[href]").size();
            int buttonCount = doc.select("button, input[type=submit], input[type=button]").size();
            int inputCount = doc.select("input:not([type=hidden]):not([type=submit]):not([type=button])").size();
            int selectCount = doc.select("select").size();
            int textareaCount = doc.select("textarea").size();

            log.info("Analysis complete for {}: forms={}, links={}, buttons={}, inputs={}, selects={}, textareas={}", 
                    url, formCount, linkCount, buttonCount, inputCount, selectCount, textareaCount);

            result.put("formCount", formCount);
            result.put("linkCount", linkCount);
            result.put("buttonCount", buttonCount);
            result.put("inputCount", inputCount);
            result.put("selectCount", selectCount);
            result.put("textareaCount", textareaCount);
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
            log.info("Found {} navigation items", navItems.size());
            result.put("navigationItems", navItems);

            log.info("Total analysis time for {}: {}ms", url, (System.currentTimeMillis() - start));
            return result;

        } catch (Exception e) {
            log.error("Page analysis failed for {} after {}ms: {}", url, (System.currentTimeMillis() - start), e.getMessage());
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Full page analysis: metadata + element breakdown.
     */
    public Map<String, Object> analyzePage(String url) {
        String html = fetchPageHtml(url);
        if (html == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("url", url);
            result.put("error", "Failed to fetch page");
            return result;
        }
        return analyzePage(html, url);
    }

    /**
     * Parse HTML into a Jsoup Document.
     */
    public Document parseHtml(String html) {
        return Jsoup.parse(html);
    }
}
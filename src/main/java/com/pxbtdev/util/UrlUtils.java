package com.pxbtdev.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UrlUtils {

    /**
     * Normalizes a URL by trimming, fixing slashes and prepending protocol if missing.
     */
    public static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            log.debug("URL normalization requested for null or blank string");
            return url;
        }

        log.info("Attempting to normalize URL input: [{}]", url);
        
        // Trim and replace backslashes (common user error)
        String n = url.trim().replace("\\", "/");
        
        // Fix cases like https:/www.google.com or https:///www.google.com
        n = n.replaceFirst("^(https?:)/+", "$1//");
        
        // Ensure it starts with a protocol
        if (!n.toLowerCase().startsWith("http://") && !n.toLowerCase().startsWith("https://")) {
            // Check for localhost or 127.0.0.1 - default to http for these
            if (n.startsWith("localhost") || n.startsWith("127.0.0.1")) {
                n = "http://" + n;
                log.info("URL detected as localhost/127.0.0.1, prepending http:// protocol");
            } else {
                n = "https://" + n;
                log.info("URL missing protocol prefix, prepending https:// as default");
            }
        }
        
        log.info("URL normalized successfully: [{}]", n);
        return n;
    }
}

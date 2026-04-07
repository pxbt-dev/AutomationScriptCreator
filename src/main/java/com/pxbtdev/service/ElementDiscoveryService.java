package com.pxbtdev.service;

import com.pxbtdev.model.entity.InteractiveElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Discovers and scores all interactive elements on a page.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElementDiscoveryService {

    private final WebPageAnalyzerService analyzerService;

    public List<InteractiveElement> discoverElements(String html, String baseUrl) {
        log.info("Starting element discovery on page: [{}]", baseUrl);
        Document doc = analyzerService.parseHtml(html);
        List<InteractiveElement> elements = new ArrayList<>();

        // Buttons
        List<InteractiveElement> buttons = extractButtons(doc);
        log.info("Discovered {} buttons/clickable elements", buttons.size());
        elements.addAll(buttons);

        // Inputs
        List<InteractiveElement> inputs = extractInputs(doc);
        log.info("Discovered {} input fields", inputs.size());
        elements.addAll(inputs);

        // Links
        List<InteractiveElement> links = extractLinks(doc, baseUrl);
        log.info("Discovered {} hyperlinks", links.size());
        elements.addAll(links);

        // Selects
        List<InteractiveElement> selects = extractSelects(doc);
        log.info("Discovered {} dropdown selects", selects.size());
        elements.addAll(selects);

        // Textareas
        List<InteractiveElement> textareas = extractTextareas(doc);
        log.info("Discovered {} textarea fields", textareas.size());
        elements.addAll(textareas);

        // Forms
        List<InteractiveElement> forms = extractForms(doc);
        log.info("Discovered {} forms", forms.size());
        elements.addAll(forms);

        log.info("Total raw elements discovered: {}", elements.size());

        // Deduplicate by selector and score
        elements = deduplicateAndSort(elements);

        log.info("Final unique interactive elements count after deduplication: {} for {}", elements.size(), baseUrl);
        return elements;
    }

    private List<InteractiveElement> extractButtons(Document doc) {
        List<InteractiveElement> result = new ArrayList<>();
        Elements buttons = doc.select("button, input[type=submit], input[type=button], [role=button]");
        for (Element el : buttons) {
            String label = el.text().trim();
            if (label.isEmpty())
                label = el.attr("value").trim();
            if (label.isEmpty())
                label = el.attr("aria-label").trim();
            if (label.isEmpty())
                label = el.attr("title").trim();

            String selector = generateSelector(el);
            log.debug("Extracted button: [{}] with selector: [{}]", label, selector);

            result.add(InteractiveElement.builder()
                    .selector(selector)
                    .elementType("button")
                    .actionType("click")
                    .label(label)
                    .tagName(el.tagName())
                    .id(el.id())
                    .name(el.attr("name"))
                    .priority(scoreButton(el, label))
                    .visible(true)
                    .build());
        }
        return result;
    }

    private List<InteractiveElement> extractInputs(Document doc) {
        List<InteractiveElement> result = new ArrayList<>();
        Elements inputs = doc.select(
                "input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=reset]):not([type=image])");
        for (Element el : inputs) {
            String inputType = el.attr("type");
            if (inputType.isEmpty())
                inputType = "text";

            String label = findLabel(doc, el);

            result.add(InteractiveElement.builder()
                    .selector(generateSelector(el))
                    .elementType("input")
                    .actionType(inputType.equals("checkbox") || inputType.equals("radio") ? "click" : "type")
                    .label(label)
                    .placeholder(el.attr("placeholder"))
                    .inputType(inputType)
                    .tagName("input")
                    .id(el.id())
                    .name(el.attr("name"))
                    .required(!el.attr("required").isEmpty())
                    .priority(scoreInput(el, inputType))
                    .visible(true)
                    .build());
        }
        return result;
    }

    private List<InteractiveElement> extractLinks(Document doc, String baseUrl) {
        List<InteractiveElement> result = new ArrayList<>();
        Elements links = doc.select("a[href]");
        int count = 0;
        for (Element el : links) {
            if (count++ > 50)
                break; // Cap links to prevent explosion
            String href = el.attr("abs:href");
            String label = el.text().trim();
            if (label.isEmpty())
                label = el.attr("aria-label").trim();
            if (label.isEmpty() || href.isEmpty() || href.startsWith("javascript:") || href.startsWith("mailto:"))
                continue;

            result.add(InteractiveElement.builder()
                    .selector(generateSelector(el))
                    .elementType("link")
                    .actionType("navigate")
                    .label(label)
                    .tagName("a")
                    .id(el.id())
                    .href(href)
                    .priority(scoreLink(el, href, baseUrl))
                    .visible(true)
                    .build());
        }
        return result;
    }

    private List<InteractiveElement> extractSelects(Document doc) {
        List<InteractiveElement> result = new ArrayList<>();
        for (Element el : doc.select("select")) {
            String label = findLabel(doc, el);
            result.add(InteractiveElement.builder()
                    .selector(generateSelector(el))
                    .elementType("select")
                    .actionType("select")
                    .label(label)
                    .tagName("select")
                    .id(el.id())
                    .name(el.attr("name"))
                    .required(!el.attr("required").isEmpty())
                    .priority(70)
                    .visible(true)
                    .build());
        }
        return result;
    }

    private List<InteractiveElement> extractTextareas(Document doc) {
        List<InteractiveElement> result = new ArrayList<>();
        for (Element el : doc.select("textarea")) {
            String label = findLabel(doc, el);
            result.add(InteractiveElement.builder()
                    .selector(generateSelector(el))
                    .elementType("textarea")
                    .actionType("type")
                    .label(label)
                    .placeholder(el.attr("placeholder"))
                    .tagName("textarea")
                    .id(el.id())
                    .name(el.attr("name"))
                    .priority(65)
                    .visible(true)
                    .build());
        }
        return result;
    }

    private List<InteractiveElement> extractForms(Document doc) {
        List<InteractiveElement> result = new ArrayList<>();
        int idx = 0;
        for (Element form : doc.select("form")) {
            String action = form.attr("action");
            String method = form.attr("method").toUpperCase();
            if (method.isEmpty())
                method = "GET";
            result.add(InteractiveElement.builder()
                    .selector("form:nth-of-type(" + (++idx) + ")")
                    .elementType("form")
                    .actionType("submit")
                    .label("Form [" + method + "] " + (action.isEmpty() ? "(self)" : action))
                    .tagName("form")
                    .id(form.id())
                    .priority(85)
                    .visible(true)
                    .build());
        }
        return result;
    }

    private String findLabel(Document doc, Element el) {
        // Try associated <label for="id">
        if (!el.id().isEmpty()) {
            Element label = doc.selectFirst("label[for=" + el.id() + "]");
            if (label != null)
                return label.text().trim();
        }
        // Try placeholder
        String ph = el.attr("placeholder");
        if (!ph.isEmpty())
            return ph;
        // Try aria-label
        String aria = el.attr("aria-label");
        if (!aria.isEmpty())
            return aria;
        // Try name
        String name = el.attr("name");
        return name.isEmpty() ? "" : name;
    }

    private String generateSelector(Element el) {
        // 1. ID selector is most stable
        if (!el.id().isEmpty())
            return "#" + el.id();

        // 2. Data-testid or data-qa (if present)
        if (!el.attr("data-testid").isEmpty())
            return "[data-testid='" + el.attr("data-testid") + "']";
        if (!el.attr("data-qa").isEmpty())
            return "[data-qa='" + el.attr("data-qa") + "']";

        // 3. Name attribute
        if (!el.attr("name").isEmpty()) {
            String selector = el.tagName() + "[name='" + el.attr("name") + "']";
            // Check if unique in its form parent
            Element form = el.closest("form");
            if (form != null && form.select(selector).size() > 1) {
                // If not unique in form, add form context
                if (!form.id().isEmpty()) return "#" + form.id() + " " + selector;
            }
            return selector;
        }

        // 4. Text content for buttons/links (if short and unique-ish)
        String text = el.text().trim();
        if (!text.isEmpty() && text.length() < 40) {
            String textSelector = el.tagName() + ":has-text('" + text.replace("'", "\\'") + "')";
            // Verify if it's likely to be unique or if we should add parent context
            if (el.tagName().equals("button") || el.tagName().equals("a")) {
                return textSelector;
            }
        }

        // 5. Class-based (take first meaningful class)
        String classes = el.className().trim();
        if (!classes.isEmpty()) {
            String firstClass = classes.split("\\s+")[0];
            // Avoid obfuscated or dynamic classes (often contain numbers or are very short)
            if (!firstClass.isEmpty() && !firstClass.matches(".*\\d.*") && firstClass.length() > 2) {
                String selector = el.tagName() + "." + firstClass;
                // Add parent context for better specificity
                Element parent = el.parent();
                if (parent != null && !parent.id().isEmpty()) {
                    return "#" + parent.id() + " " + selector;
                }
                return selector;
            }
        }

        // 6. Attribute-based (type, placeholder, aria-label)
        if (!el.attr("type").isEmpty())
            return el.tagName() + "[type='" + el.attr("type") + "']";
        if (!el.attr("placeholder").isEmpty())
            return el.tagName() + "[placeholder='" + el.attr("placeholder") + "']";
        if (!el.attr("aria-label").isEmpty())
            return el.tagName() + "[aria-label='" + el.attr("aria-label") + "']";

        // 7. Fallback to nth-of-type (less stable but guaranteed)
        int index = 1;
        Element prev = el.previousElementSibling();
        while (prev != null) {
            if (prev.tagName().equals(el.tagName())) index++;
            prev = prev.previousElementSibling();
        }
        
        String selector = el.tagName() + ":nth-of-type(" + index + ")";
        Element parent = el.parent();
        if (parent != null && !parent.id().isEmpty()) {
            return "#" + parent.id() + " > " + selector;
        }
        
        return selector;
    }

    private int scoreButton(Element el, String label) {
        int score = 50;
        String l = label.toLowerCase();
        if (l.matches(".*(submit|login|sign in|register|send|search|buy|checkout|confirm|save|continue).*"))
            score += 40;
        if (l.matches(".*(cancel|close|reset|clear).*"))
            score -= 10;
        if (!el.id().isEmpty())
            score += 10;
        return Math.min(100, score);
    }

    private int scoreInput(Element el, String type) {
        int score = 60;
        if (List.of("email", "password", "tel", "number").contains(type))
            score += 20;
        if (!el.attr("required").isEmpty())
            score += 15;
        if (!el.id().isEmpty())
            score += 5;
        return Math.min(100, score);
    }

    private int scoreLink(Element el, String href, String baseUrl) {
        int score = 30;
        // Internal links score higher (simple host extraction via split)
        if (baseUrl != null) {
            try {
                String host = new java.net.URI(baseUrl).getHost();
                if (host != null && href.contains(host))
                    score += 20;
            } catch (java.net.URISyntaxException ignored) {
                /* skip scoring */ }
        }
        // Nav links
        if (el.parents().select("nav, header, [role=navigation]").size() > 0)
            score += 25;
        // Not empty text
        if (!el.text().trim().isEmpty())
            score += 10;
        return Math.min(100, score);
    }

    private List<InteractiveElement> deduplicateAndSort(List<InteractiveElement> elements) {
        Map<String, InteractiveElement> unique = new LinkedHashMap<>();
        for (InteractiveElement el : elements) {
            unique.putIfAbsent(el.getSelector(), el);
        }
        List<InteractiveElement> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingInt(InteractiveElement::getPriority).reversed());
        return sorted;
    }
}

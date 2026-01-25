package com.pxbtdev.service;

import com.pxbtdev.model.entity.InteractiveElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElementDiscoveryService {

    public List<InteractiveElement> discoverInteractiveElements(String html, String baseUrl) {
        List<InteractiveElement> elements = new ArrayList<>();
        Document doc = Jsoup.parse(html, baseUrl);

        // Find clickable elements
        elements.addAll(discoverClickableElements(doc));

        // Find form elements
        elements.addAll(discoverFormElements(doc));

        // Find navigation elements
        elements.addAll(discoverNavigationElements(doc));

        // Sort by importance/visibility
        elements.sort(Comparator.comparing(InteractiveElement::getPriority).reversed());

        log.info("Discovered {} interactive elements", elements.size());
        return elements;
    }

    private List<InteractiveElement> discoverClickableElements(Document doc) {
        List<InteractiveElement> clickables = new ArrayList<>();

        // Buttons
        Elements buttons = doc.select("button, input[type=button], input[type=submit], input[type=reset]");
        buttons.forEach(btn -> clickables.add(createElement(btn, "CLICK", "Button")));

        // Links
        Elements links = doc.select("a[href]");
        links.forEach(link -> clickables.add(createElement(link, "CLICK", "Link")));

        // Clickable divs/spans
        Elements clickableDivs = doc.select("[onclick], [role=button]");
        clickableDivs.forEach(div -> clickables.add(createElement(div, "CLICK", "Clickable Element")));

        return clickables;
    }

    private List<InteractiveElement> discoverFormElements(Document doc) {
        List<InteractiveElement> formElements = new ArrayList<>();

        // Input fields
        Elements inputs = doc.select("input[type=text], input[type=email], input[type=password], input[type=number], textarea");
        inputs.forEach(input -> {
            String type = getInputType(input);
            String testValue = generateTestValue(input);
            formElements.add(createFormElement(input, "TYPE", type, testValue));
        });

        // Select dropdowns
        Elements selects = doc.select("select");
        selects.forEach(select -> formElements.add(createElement(select, "SELECT", "Dropdown")));

        // Checkboxes and radios
        Elements checkables = doc.select("input[type=checkbox], input[type=radio]");
        checkables.forEach(cb -> formElements.add(createElement(cb, "CLICK", "Checkable")));

        return formElements;
    }

    private List<InteractiveElement> discoverNavigationElements(Document doc) {
        // Find breadcrumbs, pagination, tabs
        List<InteractiveElement> navElements = new ArrayList<>();

        // Breadcrumbs
        Elements breadcrumbs = doc.select("[aria-label*=breadcrumb], .breadcrumb, nav");
        breadcrumbs.forEach(bc -> navElements.add(createElement(bc, "NAVIGATE", "Navigation")));

        // Pagination
        Elements pagination = doc.select(".pagination, [aria-label*=pagination]");
        pagination.forEach(pg -> navElements.add(createElement(pg, "CLICK", "Pagination")));

        return navElements;
    }

    private InteractiveElement createElement(Element element, String actionType, String elementType) {
        String selector = generateCssSelector(element);
        String text = element.text().substring(0, Math.min(element.text().length(), 50));

        return InteractiveElement.builder()
                .actionType(actionType)
                .elementType(elementType)
                .selector(selector)
                .text(text)
                .tagName(element.tagName())
                .id(element.id())
                .classes(element.className())
                .priority(calculatePriority(element))
                .build();
    }

    private String generateCssSelector(Element element) {
        // Generate optimal CSS selector
        if (!element.id().isEmpty()) {
            return "#" + element.id();
        }

        // Build selector with classes
        List<String> selectorParts = new ArrayList<>();
        selectorParts.add(element.tagName());

        if (!element.className().isEmpty()) {
            String firstClass = element.className().split(" ")[0];
            selectorParts.add("." + firstClass);
        }

        // Add attributes for uniqueness
        if (element.hasAttr("name")) {
            selectorParts.add("[name=\"" + element.attr("name") + "\"]");
        } else if (element.hasAttr("type")) {
            selectorParts.add("[type=\"" + element.attr("type") + "\"]");
        }

        return String.join("", selectorParts);
    }

    private int calculatePriority(Element element) {
        int priority = 0;

        // Higher priority for visible/important elements
        if (!element.id().isEmpty()) priority += 20;
        if (element.hasAttr("data-testid") || element.hasAttr("data-qa")) priority += 30;
        if (element.tagName().equals("button") || element.tagName().equals("a")) priority += 10;
        if (element.hasText() && element.text().length() > 2) priority += 5;

        return priority;
    }

    private String getInputType(Element input) {
        String type = input.attr("type");
        if (type.isEmpty()) return "text";
        return type;
    }

    private String generateTestValue(Element input) {
        String type = input.attr("type").toLowerCase();

        switch (type) {
            case "email": return "test@example.com";
            case "password": return "TestPassword123!";
            case "number": return "42";
            case "tel": return "123-456-7890";
            case "date": return "2024-01-15";
            default: return "Test value";
        }
    }

    private InteractiveElement createFormElement(Element element, String actionType, String inputType, String testValue) {
        InteractiveElement el = createElement(element, actionType, inputType + " Input");
        el.setTestValue(testValue);
        return el;
    }
}
package com.pxbtdev.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    @Bean(name = "testCaseEnhancerClient")
    public ChatClient testCaseEnhancerClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem("""
                    You are a QA automation expert. Enhance test cases.
                    Respond ONLY in this format:
                    
                    ENHANCED_STEPS:
                    1. [enhanced step]
                    2. [enhanced step]
                    3. [enhanced step]
                    
                    ENHANCED_RESULTS:
                    1. [verifiable result]
                    2. [verifiable result]
                    3. [verifiable result]
                    
                    EDGE_CASES:
                    - [edge case]
                    - [edge case]
                    
                    No explanations, no markdown, no other text.
                    """)
                .build();
    }

    @Bean(name = "generalAssistantClient")
    public ChatClient generalAssistantClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem("""
                    You are a helpful software testing assistant.
                    Provide concise, accurate answers to questions about:
                    - Test automation
                    - QA best practices
                    - Playwright/Selenium testing
                    - Test case design
                    
                    For technical questions, provide practical advice.
                    """)
                .build();
    }

    @Bean(name = "simpleChatClient")
    public ChatClient simpleChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultSystem("You are a helpful assistant. Answer questions directly and concisely.")
                .build();
    }
}
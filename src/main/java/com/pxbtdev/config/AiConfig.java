package com.pxbtdev.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
public class AiConfig {

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    @Bean
    @ConditionalOnMissingBean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    // MAIN ChatClient bean - name it "chatClient" to match what services expect
    @Bean(name = "chatClient")
    @Primary
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new SimpleLoggerAdvisor()
                )
                .defaultSystem("""
                    You are a helpful QA automation assistant. Your primary role is to enhance test cases.
                    
                    FOR TEST CASE ENHANCEMENT REQUESTS:
                    - You will receive a test case to enhance
                    - Respond ONLY in this exact format:
                    
                    ENHANCED_STEPS:
                    1. [enhanced step 1]
                    2. [enhanced step 2]
                    3. [enhanced step 3]
                    
                    ENHANCED_RESULTS:
                    1. [specific verifiable result 1]
                    2. [specific verifiable result 2]
                    3. [specific verifiable result 3]
                    
                    EDGE_CASES:
                    - [practical edge case 1]
                    - [practical edge case 2]
                    
                    FOR ALL OTHER REQUESTS:
                    - Respond naturally and helpfully
                    - Don't use the test case format unless explicitly asked
                    """)
                .build();
    }

    // Alternative simple chat client
    @Bean(name = "simpleChatClient")
    @ConditionalOnMissingBean
    public ChatClient simpleChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are a helpful QA automation assistant.")
                .build();
    }

    // Fallback bean when AI is disabled
    @Bean(name = "chatClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "false", matchIfMissing = false)
    public ChatClient disabledChatClient() {
        return null;
    }
}
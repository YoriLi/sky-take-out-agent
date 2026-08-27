package com.sky.agent.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨请求存活的会话记忆。key 为 conversationId（默认 empId）。
 */
@Component
public class ChatMemoryStore {

    private final ConcurrentHashMap<String, ChatMemory> memories = new ConcurrentHashMap<String, ChatMemory>();

    public ChatMemory getOrCreate(String conversationId, int maxMessages) {
        final int window = maxMessages < 1 ? 20 : maxMessages;
        return memories.computeIfAbsent(conversationId, id -> MessageWindowChatMemory.withMaxMessages(window));
    }
}

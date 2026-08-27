package com.sky.agent.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 跨请求存活的会话记忆。key 为已与 empId 绑定的 conversationId。
 * 用带容量上限的 LRU 保存，防止攻击者用大量伪造 conversationId 撑爆堆内存。
 */
@Component
public class ChatMemoryStore {

    /** 最多保留的会话数，超出后按最近最少使用淘汰 */
    private static final int MAX_CONVERSATIONS = 1000;

    private final Map<String, ChatMemory> memories =
            new LinkedHashMap<String, ChatMemory>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ChatMemory> eldest) {
                    return size() > MAX_CONVERSATIONS;
                }
            };

    public synchronized ChatMemory getOrCreate(String conversationId, int maxMessages) {
        final int window = maxMessages < 1 ? 20 : maxMessages;
        ChatMemory memory = memories.get(conversationId);
        if (memory == null) {
            memory = MessageWindowChatMemory.withMaxMessages(window);
            memories.put(conversationId, memory);
        }
        return memory;
    }
}

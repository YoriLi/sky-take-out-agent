package com.sky.agent.dto;

import lombok.Data;

/**
 * 管理端 AI 助手对话请求。
 */
@Data
public class AgentChatRequest {

    /**
     * 用户本轮输入，必填。
     */
    private String message;

    /**
     * 会话 id，可空；默认使用当前登录员工 empId。
     */
    private String conversationId;
}

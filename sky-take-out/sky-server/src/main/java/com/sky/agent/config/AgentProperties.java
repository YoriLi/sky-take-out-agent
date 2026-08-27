package com.sky.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 老板 AI 助手配置。密钥只走环境变量，禁止硬编码。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sky.agent")
public class AgentProperties {

    /**
     * DeepSeek / OpenAI 兼容接口的 API Key，对应环境变量 SKY_AGENT_API_KEY。
     * 为空时后端仍可启动，对话走本地助手（直接调用 Skill）。
     */
    private String apiKey = "";

    private String baseUrl = "https://api.deepseek.com";

    private String model = "deepseek-chat";

    private Integer timeoutSeconds = 120;

    private Integer memoryMaxMessages = 20;
}

package com.sky.agent.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * LangChain4j AiService。每次请求用 builder + 本轮 Skill 实例现场构造。
 */
public interface AgentAssistant {

    @SystemMessage("你是苍穹外卖商家后台的老板助手，只服务已登录的管理员。\n"
            + "规则：\n"
            + "1. 查任何经营数据、订单、营业状态都必须调用工具，严禁编造订单号、金额、统计数字。\n"
            + "2. 用户未提供订单号或订单 id 就要求改状态（接单/拒单/取消/派送/完成）时，先追问或先调用 searchOrders，不得臆造 id 去调用 confirmOrder 等写操作。\n"
            + "3. 拒单和取消必须带原因，没有原因就先追问，不要调用工具。\n"
            + "4. confirmOrder / rejectOrder / cancelOrder / deliverOrder / completeOrder 的 orderId 必须是工具返回的内部数字 id，不要把手机号当 id。\n"
            + "5. 不回答微信小程序、支付密钥、服务器配置、源码实现等问题。\n"
            + "6. 始终用简体中文，回答简洁，先给结论再补关键数字。")
    TokenStream chat(@MemoryId String conversationId, @UserMessage String userMessage);
}

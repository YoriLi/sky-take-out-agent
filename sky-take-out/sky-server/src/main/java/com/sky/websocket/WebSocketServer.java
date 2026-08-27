package com.sky.websocket;

import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket服务
 */
/*
 * WebSocket 服务类
 * 作用：实现商家端（浏览器）与后端的长连接通信。
 * 当用户下单或催单时，后端通过 WebSocket 主动向前端推送实时消息，
 * 商家无需刷新页面即可听到“您有新的订单”的提醒。
 * HTTP 是“请求-响应”模式（一问一答），
 * WebSocket 是“长连接”模式（可以主动向客户端推送数据）。
 */
@Component
@ServerEndpoint("/ws/{sid}")
// @ServerEndpoint：声明这是一个 WebSocket 端点（服务端接入点）。
// 路径为 "/ws/{sid}"，其中 {sid} 是路径变量（Session ID），
// 用于区分不同的连接客户端（比如区分是“商家A”还是“商家B”）。
// 前端（商家浏览器）连接时，会访问 ws://localhost:8080/ws/商家ID
public class WebSocketServer {
    //存放会话对象
    private static Map<String, Session> sessionMap = new HashMap();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    // 标记为“连接建立”事件处理器
    public void onOpen(Session session, @PathParam("sid") String sid) {
        System.out.println("客户端：" + sid + "建立连接");
        sessionMap.put(sid, session);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        System.out.println("收到来自客户端：" + sid + "的信息:" + message);
    }

    /**
     * 连接关闭调用的方法
     *
     * @param sid
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        System.out.println("连接断开:" + sid);
        sessionMap.remove(sid);
    }

    /**
     * 群发
     *
     * @param message
     */
    public void sendToAllClient(String message) {
        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
            try {
                //服务器向客户端发送消息
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

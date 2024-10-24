package com.twm.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twm.bot.model.ChatMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessageSendingOperations stompMessagingTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RedisMessageSubscriber(SimpMessageSendingOperations stompMessagingTemplate, ObjectMapper objectMapper) {
        this.stompMessagingTemplate = stompMessagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String messageBody = new String(message.getBody());
        log.info("Received message from Redis channel {}: {}", channel, messageBody);

        try {
            if (channel.startsWith("chat:")) {
                String chatSessionId = channel.substring(5); // 移除 "chat:" 前綴
                ChatMessage chatMessage = objectMapper.readValue(messageBody, ChatMessage.class);
                stompMessagingTemplate.convertAndSend("/queue/agent/" + chatSessionId, chatMessage);
            } else {
                // 處理其他類型的消息...
                switch (channel) {
                    case "humanSupportQueueChannel":
                        stompMessagingTemplate.convertAndSend("/topic/humanSupportQueue", messageBody);
                        break;
                    case "chatSessionSwitchChannel":
                        stompMessagingTemplate.convertAndSend("/topic/chatSessionSwitch", messageBody);
                        break;
                    default:
                        log.warn("Received message on unknown channel: {}", channel);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Redis message", e);
        }
    }
}
package com.twm.bot.service;

import com.twm.bot.model.ChatMessage;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateChatSession(String chatSessionId, ChatMessage message) {
        String chatMessageKey = "chatSession:" + chatSessionId + ".chatMessages";
        log.info("Updating chat session: {} with message: {}", chatSessionId, message);

        Map<String, Object> simplifiedMessage = new HashMap<>();
        simplifiedMessage.put("sender", message.getSender());
        simplifiedMessage.put("content", message.getContent());
        simplifiedMessage.put("type", message.getType().toString());

        Instant timestamp = message.getTimestamp() != null ? message.getTimestamp() : Instant.now();
        simplifiedMessage.put("timestamp", timestamp.toString());

        redisTemplate.opsForList().rightPush(chatMessageKey, simplifiedMessage);
    }

    public List<ChatMessage> getChatSessionMessages(String chatSessionId) {
        List<Object> objects = redisTemplate.opsForList().range("chatSession:" + chatSessionId + ".chatMessages", 0, -1);

        if (objects == null) {
            return Collections.emptyList();
        }

        return objects.stream()
                .map(this::convertToChatMessage)
                .collect(Collectors.toList());
    }

    private ChatMessage convertToChatMessage(Object obj) {
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            String sender = (String) map.get("sender");
            String content = (String) map.get("content");
            ChatMessage.MessageType type = ChatMessage.MessageType.valueOf((String) map.get("type"));
            Instant timestamp = Instant.parse((String) map.get("timestamp"));
            return new ChatMessage(sender, content, type, timestamp);
        } else {
            throw new RuntimeException("Unexpected data format in Redis");
        }
    }

    // 保存用戶 ID 和 Email
    public void saveUserSession(String chatSessionId, String userId, String email) {
        String userSessionKey = "chatSession:" + chatSessionId + ".userId";
        String userEmailKey = "chatSession:" + chatSessionId + ".email";

        redisTemplate.opsForValue().set(userSessionKey, userId);
        redisTemplate.opsForValue().set(userEmailKey, email);
    }

    // 檢索用戶 ID
    public String getUserSession(String chatSessionId) {
        String userSessionKey = "chatSession:" + chatSessionId + ".userId";
        return (String) redisTemplate.opsForValue().get(userSessionKey);
    }

    // 檢索用戶 Email
    public String getUserEmailBySession(String chatSessionId) {
        String userEmailKey = "chatSession:" + chatSessionId + ".email";
        return (String) redisTemplate.opsForValue().get(userEmailKey);
    }

    // 增加未回答問題的計數
    public void incrementUnansweredCount(String chatSessionId) {
        String key = "chatSession:" + chatSessionId + ".unansweredCount";
        redisTemplate.opsForValue().increment(key);
    }

    // 獲取未回答問題的計數
    public int getUnansweredCount(String chatSessionId) {
        String key = "chatSession:" + chatSessionId + ".unansweredCount";
        Object count = redisTemplate.opsForValue().get(key);
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    // 重置未回答問題的計數
    public void resetUnansweredCount(String chatSessionId) {
        String key = "chatSession:" + chatSessionId + ".unansweredCount";
        redisTemplate.delete(key);
    }

    public void addToHumanSupportQueue(String chatSessionId) {
        redisTemplate.opsForList().rightPush("humanSupportQueue", chatSessionId);
    }

    // 將 agent 分配到某個 chatSession
    public void assignAgentToSession(String chatSessionId, String agentId) {
        String agentSessionKey = "agentSession:" + chatSessionId + ".agentId";
        redisTemplate.opsForValue().set(agentSessionKey, agentId);
    }

    public void setAgentHandling(String chatSessionId, boolean isHandling) {
        log.info("Setting agent handling for ChatSessionId: {} to {}", chatSessionId, isHandling);
        redisTemplate.opsForHash().put("chatSession:" + chatSessionId, "isAgentHandling", isHandling);
    }

    public boolean isAgentHandling(String chatSessionId) {
        Boolean isHandling = (Boolean) redisTemplate.opsForHash().get("chatSession:" + chatSessionId, "isAgentHandling");
        log.info("Checking if agent is handling for ChatSessionId: {} - Result: {}", chatSessionId, isHandling);
        return isHandling != null && isHandling;
    }

    public void clearSessionData(String chatSessionId) {
        String chatMessageKey = "chatSession:" + chatSessionId + ".chatMessages";
        String chatEmailKey = "chatSession:" + chatSessionId + ".email";
        String chatUnansweredCount = "chatSession:" + chatSessionId + ".unansweredCount";
        String sessionUserIdKey = "chatSession:" + chatSessionId + ".userId";

        redisTemplate.delete(chatMessageKey);
        redisTemplate.delete(sessionUserIdKey);
        redisTemplate.delete(chatEmailKey);
        redisTemplate.delete(chatUnansweredCount);
    }

    public void saveCaptcha(String captchaId, String captchaText, Duration expiration) {
        String key = "captcha:" + captchaId;
        redisTemplate.opsForValue().set(key, captchaText, expiration);
    }

    public String getCaptcha(String captchaId) {
        String key = "captcha:" + captchaId;
        return (String) redisTemplate.opsForValue().get(key);
    }

    public void deleteCaptcha(String captchaId) {
        String key = "captcha:" + captchaId;
        redisTemplate.delete(key);
    }

}

package com.twm.bot.service.impl;

import com.twm.bot.model.ChatMessage;
import com.twm.bot.service.ChatHistoryService;
import com.twm.bot.service.ElasticsearchService;
import com.twm.bot.service.RedisService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {
    private final ElasticsearchService elasticsearchService;
    private final RedisService redisService;

    public ChatHistoryServiceImpl(ElasticsearchService elasticsearchService, RedisService redisService) {
        this.elasticsearchService = elasticsearchService;
        this.redisService = redisService;
    }

    @Override
    public void saveHistory(long userId, String chatSessionId, List<ChatMessage> chatMessagesList) {
        for (ChatMessage chatMessage: chatMessagesList) {
            try {
                chatMessage.setChatSessionId(chatSessionId);
                elasticsearchService.saveChatMessage(userId, chatMessage);
                redisService.clearSessionData(chatSessionId);
            } catch (Exception e) {
                throw new RuntimeException("Error saving chat history");
            }
        }
    }

    @Override
    public List<ChatMessage> getHistory(long userId) {
        try {
            return elasticsearchService.findLatestMessagesByUserId(userId);
        } catch (Exception e) {
            throw new RuntimeException("Error getting chat hisgory of userId: " + userId);
        }
    }
}

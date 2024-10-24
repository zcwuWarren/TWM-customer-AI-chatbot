package com.twm.bot.service;

import com.twm.bot.model.ChatMessage;

import java.util.List;

public interface ChatHistoryService {
    void saveHistory(long userId, String chatSessionId, List<ChatMessage> chatMessagesList);
    List<ChatMessage> getHistory(long userId);
}

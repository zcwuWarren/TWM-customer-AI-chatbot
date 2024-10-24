package com.twm.bot.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String chatSessionId;
    private String sender;
    private String content;
    private MessageType type;
    private Instant timestamp; // 使用 Instant 來表示 UTC 時間戳

    public enum MessageType {
        CHAT, JOIN, LEAVE, FAQ_SUGGESTIONS, SUGGESTIONS, INITIAL_FAQ, REQUEST_AGENT
    }

    // 更新構造函數，包含時間戳
    public ChatMessage(String sender, String content, MessageType type) {
        this(sender, content, type, Instant.now()); // 使用 UTC 當前時間作為預設值
    }

    public ChatMessage(String sender, String content, MessageType type, Instant timestamp) {
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp != null ? timestamp : Instant.now(); // 確保 timestamp 不為 null
    }
}

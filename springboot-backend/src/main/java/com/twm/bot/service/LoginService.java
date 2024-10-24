package com.twm.bot.service;

import com.twm.bot.data.dto.LogEntry;
import com.twm.bot.data.dto.LoginDto;
import com.twm.bot.model.user.User;
import lombok.Data;

import java.util.List;
import java.util.UUID;


public interface LoginService  {
    LoginDto userLoginNative(String email, String password, String captchaId, String captcha);
    void logOut(String jtwToken);

    LoginDto userLoginTWM(String email);

    User updateUserTerms(String accessToken);

    @Data
    class SessionData {
        private User user;
        private List<LogEntry> logs;  // List of key-value pairs for logs
        private List<String> chatHistory;  // List of chat messages
        private final UUID sessionId;   // Unique identifier for chat history

        public SessionData(User user, List<LogEntry> logs, List<String> chatHistory) {
            this.user = user;
            this.logs = logs;
            this.chatHistory = chatHistory;
            this.sessionId = UUID.randomUUID();
        }

        public SessionData(User user, List<LogEntry> logs, List<String> chatHistory, UUID chatId) {
            this.user = user;
            this.logs = logs;
            this.chatHistory = chatHistory;
            this.sessionId = chatId;
        }
    }
}

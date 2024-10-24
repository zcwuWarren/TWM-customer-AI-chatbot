package com.twm.bot.controller;

import com.twm.bot.data.dto.SearchResponse;
import com.twm.bot.model.ChatMessage;
import com.twm.bot.model.user.User;
import com.twm.bot.service.ChatHistoryService;
import com.twm.bot.service.RedisService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat-history")
public class ChatHistoryController {

    @Autowired
    private final ChatHistoryService chatHistoryService;
    private final RedisService redisService;

    public ChatHistoryController(ChatHistoryService chatHistoryService, RedisService redisService) {
        this.chatHistoryService = chatHistoryService;
        this.redisService = redisService;
    }

    @PostMapping("/save")
    public ResponseEntity<String> saveChatHistory(
            @RequestParam("chatSessionId") String chatSessionId) {
        String userId = redisService.getUserSession(chatSessionId);
        List<ChatMessage> chatMessagesList = redisService.getChatSessionMessages(chatSessionId);
        chatHistoryService.saveHistory(Long.parseLong(userId), chatSessionId, chatMessagesList);
        return new ResponseEntity<>("Chat history saved successfully", HttpStatus.OK);
    }

    @GetMapping("/latest-messages")
    public ResponseEntity<?> getLatestMessagesByUserId() {
        try {
            // Get the authenticated user's ID from the security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User user =  (User) authentication.getPrincipal();  // Replace with your UserDetails implementation
            long userId = user.getId();  // Assuming your CustomUserDetails class has a method to get the user ID

            // Retrieve chat history using the user ID
            List<ChatMessage> latestMessages = chatHistoryService.getHistory(userId);
            if (latestMessages.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
            return new ResponseEntity<>(new SearchResponse<>(latestMessages), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

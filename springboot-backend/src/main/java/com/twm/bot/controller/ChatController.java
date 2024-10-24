package com.twm.bot.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twm.bot.model.ChatMessage;
import com.twm.bot.model.faq.FAQ;
import com.twm.bot.model.user.User;
import com.twm.bot.service.CustomerService;
import com.twm.bot.service.ElasticsearchService;
import com.twm.bot.service.RedisService;
import com.twm.bot.service.SearchService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
@Controller
public class ChatController {

    private final RedisService redisService;
    private final SimpMessageSendingOperations stompMessagingTemplate; // 注入 SimpMessageSendingOperations
    private  Map<String, String> faqMap = new HashMap<>();
    private final CustomerService customerService;
    private final SearchService searchService;
    private final ElasticsearchService elasticsearchService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public ChatController(RedisService redisService, SimpMessageSendingOperations stompMessagingTemplate, CustomerService customerService, SearchService searchService, ElasticsearchService elasticsearchService, RedisTemplate<String, Object> redisTemplate) throws Exception {
        this.redisService = redisService;
        this.stompMessagingTemplate = stompMessagingTemplate;
        this.customerService = customerService;
        this.searchService = searchService;
        this.elasticsearchService = elasticsearchService;
        this.redisTemplate = redisTemplate;

        // 初始化 FAQ 地圖
        faqMap = elasticsearchService.getRandomFAQMap(3);
    }

    @MessageMapping("/chat.sendMessage")
    public void handleMessage(@Payload ChatMessage message, Principal principal) throws Exception {
        String chatSessionId = message.getChatSessionId();

        log.info("Received message from user: {}", message);

        if (redisService.isAgentHandling(chatSessionId)) {
            // 使用 Redis Pub/Sub 發送消息
            redisTemplate.convertAndSend("chat:" + chatSessionId, message);
            redisService.updateChatSession(chatSessionId, message);
        } else {
            // Bot 處理邏輯保持不變
            ChatMessage responseMessage = handleMessageByBot(chatSessionId, message);
            stompMessagingTemplate.convertAndSendToUser(principal.getName(), "/queue/reply/" + chatSessionId, responseMessage);
        }
    }

    private ChatMessage forwardMessageToAgent(String chatSessionId, ChatMessage message) {
        // 更新 Redis 中的聊天會話
        redisService.updateChatSession(chatSessionId, message);

        log.info("Message forwarded to agent. ChatSessionId: {}, Message: {}", chatSessionId, message);
        stompMessagingTemplate.convertAndSend("/queue/agent/" + chatSessionId, message);
        return message;
    }

    // Step 2: Bot 處理訊息
    private ChatMessage handleMessageByBot(String chatSessionId, ChatMessage message) throws Exception {
        String userMessage = message.getContent();

        // Step 2a: 更新 Redis 中的聊天會話
        redisService.updateChatSession(chatSessionId, message);

        // Step 2b: 生成 Bot 的回應
        ChatMessage responseMessage = generateBotResponse(chatSessionId, userMessage);

        // Step 2c: 更新 Redis 中的聊天會話（Bot 回應）
        redisService.updateChatSession(chatSessionId, responseMessage);
        log.info("Updated chat session in Redis for ChatSessionId: {} with message: {}", chatSessionId, responseMessage);

        return responseMessage;
    }

    private ChatMessage generateBotResponse(String chatSessionId, String userMessage) throws Exception {
        // Attempt to find an exact match in the FAQ
        Optional<FAQ> optionalFaq = searchService.exactMatchFAQ(userMessage);

        if (optionalFaq.isPresent()) {
            // If a match is found, return the FAQ answer
            return new ChatMessage("Bot", optionalFaq.get().getAnswer(), ChatMessage.MessageType.CHAT);
        }

        String aiResponse;
        try {
            aiResponse = customerService.twoStageResponse(userMessage, chatSessionId);
            //aiResponse = customerService.getAIResponseWithContext(userMessage, chatSessionId);
        } catch (Exception e) {
            throw new RuntimeException("Error while fetching AI response", e);
        }

        ChatMessage responseMessage = new ChatMessage("Bot", aiResponse, ChatMessage.MessageType.CHAT);

        if (aiResponse.contains("人工客服")) {
                redisService.incrementUnansweredCount(chatSessionId);
                int unansweredCount = redisService.getUnansweredCount(chatSessionId);

                if (unansweredCount >= 1) {
                    // If unanswered count is greater than or equal to 1, offer human support
                    responseMessage = new ChatMessage(
                            "Bot",
                            "很抱歉，我無法回答您的問題。是否需要轉接人工客服？<button onclick='requestHumanSupport()'>轉接人工客服</button>",
                            ChatMessage.MessageType.CHAT
                    );
            } else {
                // If the AI responses properly, reset the unanswered count
                redisService.resetUnansweredCount(chatSessionId);
                }
            }

            return responseMessage;
    }




    @MessageMapping("/chat.selectFaq")
    public void handleFaqSelection(@Payload ChatMessage message, Principal principal) {
        String chatSessionId = message.getChatSessionId(); // 取得 chatSessionId
        String selectedQuestion = message.getContent();
        String answer = faqMap.get(selectedQuestion);

        ChatMessage responseMessage;
        if (answer != null) {
            responseMessage = new ChatMessage("Bot", answer, ChatMessage.MessageType.CHAT);
        } else {
            responseMessage = new ChatMessage("Bot", "抱歉，我無法找到這個問題的答案。", ChatMessage.MessageType.CHAT);
        }

        // 儲存回覆訊息到 Redis
        Instant currentInstant = Instant.now(); // 獲取當前時間
        responseMessage.setTimestamp(currentInstant); // 設置 Instant 時間戳
        redisService.updateChatSession(chatSessionId, responseMessage);

        // 發送消息到指定的用戶路徑
        stompMessagingTemplate.convertAndSendToUser(principal.getName(), "/queue/reply/" + chatSessionId, responseMessage);
    }

    @MessageMapping("/chat.getInitialFAQ")
    public void getInitialFAQ(@Payload Map<String, String> payload, Principal principal) {
        // 從 payload 中獲取 chatSessionId
        String chatSessionId = payload.get("chatSessionId");

        List<String> questions = new ArrayList<>(faqMap.keySet());
        Collections.shuffle(questions);
        List<String> selectedQuestions = questions.subList(0, Math.min(3, questions.size()));

        ChatMessage responseMessage = new ChatMessage("Bot", String.join("\n", selectedQuestions), ChatMessage.MessageType.FAQ_SUGGESTIONS);
        responseMessage.setChatSessionId(chatSessionId); // 設置 chatSessionId

        // 發送消息到用戶指定的隊列，包括 chatSessionId
        stompMessagingTemplate.convertAndSendToUser(principal.getName(), "/queue/reply/" + chatSessionId, responseMessage);
    }

    @MessageMapping("/chat.getSuggestions")
    public void getSuggestions(@Payload ChatMessage message, Principal principal) throws Exception {
        String chatSessionId = message.getChatSessionId(); // 取得 chatSessionId
        String userInput = message.getContent();
        List<FAQ> suggestions = searchService.partialMatchFAQs(userInput);
        List<String> matchedQuestions = suggestions.stream()
                .map(FAQ::getQuestion)
                .limit(3)
                .collect(Collectors.toList());

        ChatMessage responseMessage;
        if (!matchedQuestions.isEmpty()) {
            responseMessage = new ChatMessage("Bot", String.join("\n", matchedQuestions), ChatMessage.MessageType.SUGGESTIONS);
        } else {
            responseMessage = new ChatMessage("Bot", "", ChatMessage.MessageType.SUGGESTIONS);
        }

        // 發送消息到指定的用戶路徑
        stompMessagingTemplate.convertAndSendToUser(principal.getName(), "/queue/reply/" + chatSessionId, responseMessage);
    }

    @MessageMapping("/chat.connect")
    public void handleConnect(@Payload Map<String, String> payload, Principal principal) {
        String chatSessionId = payload.get("chatSessionId");

        if (principal instanceof UsernamePasswordAuthenticationToken authenticationToken) {
            User user = (User) authenticationToken.getPrincipal(); // 獲取 User 物件
            String userId = user.getId().toString();
            String email = user.getEmail(); // 假設 User 物件有 getEmail() 方法
            redisService.saveUserSession(chatSessionId, userId, email);
        } else {
            throw new RuntimeException("用戶未認證");
        }
    }


    @MessageMapping("/chat.requestHumanSupport")
    public void handleHumanSupportRequest(@Payload ChatMessage message, Principal principal) {
        String chatSessionId = message.getChatSessionId();

        // 將用戶加入人工客服等待隊列
        redisService.addToHumanSupportQueue(chatSessionId);

        // 發佈消息到 Redis Pub/Sub 通道，通知 agent 有新的請求
        redisTemplate.convertAndSend("humanSupportQueueChannel", chatSessionId);

        // 發送確認消息給用戶
        ChatMessage confirmationMessage = new ChatMessage("System", "您已被加入人工客服等待隊列，請耐心等待。", ChatMessage.MessageType.REQUEST_AGENT);
        redisService.updateChatSession(chatSessionId, confirmationMessage);

        // 發送確認消息到用戶指定的隊列
        stompMessagingTemplate.convertAndSendToUser(principal.getName(), "/queue/reply/" + chatSessionId, confirmationMessage);
    }

    @MessageMapping("/chat.agentJoin")
    public void handleAgentJoin(@Payload Map<String, String> payload, Principal principal) throws JsonProcessingException {
        String chatSessionId = payload.get("chatSessionId");
        log.info("Agent joining chat session: {}", chatSessionId);

        if (principal instanceof UsernamePasswordAuthenticationToken authenticationToken) {
            User agent = (User) authenticationToken.getPrincipal();
            String agentId = agent.getId().toString();

            redisService.assignAgentToSession(chatSessionId, agentId);
            redisService.setAgentHandling(chatSessionId, true);

            log.info("Assigned agent {} to chat session {}", agentId, chatSessionId);

            stompMessagingTemplate.convertAndSend("/queue/agent/" + chatSessionId, "Agent connected");

            try {
                String chatSummary = customerService.handleHandOver(chatSessionId);
                stompMessagingTemplate.convertAndSend("/queue/agent/" + chatSessionId, chatSummary);
            } catch (Exception e) {
                log.error(e.getMessage());
            }

            log.info("Sent 'Agent connected' message to agent for ChatSessionId: {}", chatSessionId);

            // 通知用戶切換到 agent 頻道
            notifyUserToSwitch(payload);
        } else {
            log.error("Agent authentication failed for chat session: {}", chatSessionId);
            throw new RuntimeException("Agent 未認證");
        }
    }

    // 當代理加入聊天會話時，通知所有 EC2 實例上的用戶
    @MessageMapping("/chat.notifyUserToSwitch")
    public void notifyUserToSwitch(@Payload Map<String, String> payload) {
        String chatSessionId = payload.get("chatSessionId");
        log.info("Notifying users to switch to agent channel for chat session: {}", chatSessionId);

        // 將消息發送到 Redis Pub/Sub 通道
        redisTemplate.convertAndSend("chatSessionSwitchChannel", chatSessionId);
    }

}

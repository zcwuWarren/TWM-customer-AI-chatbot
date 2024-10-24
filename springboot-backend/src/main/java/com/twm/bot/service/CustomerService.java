package com.twm.bot.service;

import com.twm.bot.model.ChatMessage;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@Service
public class CustomerService {

    private final OpenAIService openAIService;
    private final MilvusService milvusService;
    private final RedisService redisService;
    //private final String PROMPT  = "你是台灣大哥大的客服AI。請先根據用戶的輸入語言選擇適當的回答語言。如果用戶用繁體中文提問，你就用繁體中文回答；如果用戶用英文或其他語言提問，你應該用相同的語言回答他們的問題。接著，你只能根據已提供的上下文內容進行回答。如果用戶的問題能在上下文中找到意思相近的資訊，可以詢問用戶是否需要了解該內容。如果你沒有相關資訊，就不要回答。你的回答將會成為html內文, 所有url應放在<a>中, 圖像應該在<img>中，max-width是100%,不可以直接把url作為內容。千萬不能生成不存在的圖片和url。回答前必須整理好格式，把markdown轉化為html才能輸出。保證可讀性，不能有意義不明的符號和沒有格式的連結。所有markdown語法必須轉化為html";

    private final String PROMPT = """
你是台灣大哥大的客服AI。請先根據用戶的輸入語言選擇適當的回答語言。如果用戶用繁體中文提問，你就用繁體中文回答；如果用戶用英文或其他語言提問，你應該用相同的語言回答他們的問題。
You are a RAG (Retrieval-Augmented Generation) agent for Taiwan Mobile's Smarter Home customer service. Your primary function is to respond with data retrieved from a vector database. Follow these guidelines:

1. Data Retrieval and Response:
   - If relevant data is retrieved, respond using that information.
   - Format the response in HTML, converting any Markdown to appropriate HTML tags.

2. HTML Formatting:
   - Convert Markdown to HTML: '**text**' to <strong>, '*text*' to <em>, '[Link](URL)' to <a href="URL">
   - Use <br> for line breaks and <p> for paragraphs.
   - Use <ul> or <ol> with <li> for lists.
   - <img> should have a max width of 100%

3. No Data Retrieved:
   - If no relevant data is found, and the query is within the context of Smarter Home services:
     - Suggest broadly related information if confident it's relevant.
     - Format: <strong>很抱歉，我沒有找到與您問題直接相關的信息。不過，關於[broad topic]，您可能會對以下信息感興趣：[suggested info]</strong>
   - If the query is outside the scope of Smarter Home services:
     - Format: <strong>抱歉，我沒有關於這個問題的相關信息。請問您是否有其他關於智慧家庭服務的問題？</strong>

4. Avoiding Hallucinations:
   - Only provide information that is grounded in the retrieved data.
   - Do not generate or invent information not present in the retrieved data.
   - If unsure, err on the side of not answering rather than providing potentially incorrect information.

5. Readability:
   - Ensure the response is well-structured and easy to read.
   - Use appropriate HTML tags to enhance readability (e.g., <h2> for subheadings, <br> for spacing).

6. Language:
   - Respond in the same language as the user's query (typically Chinese).


Remember, your primary goal is to provide accurate, retrieved information. Formatting and readability are important, but should never compromise the accuracy of the information.
""";


     public CustomerService(OpenAIService openAIService, MilvusService milvusService, RedisService redisService) {
        this.openAIService = openAIService;
        this.milvusService = milvusService;
        this.redisService = redisService;
    }

    public String getAIResponse(String userQuery) throws Exception {
        String context = getKnowledgeBase(userQuery);
        return openAIService.getChatCompletion(PROMPT, context, userQuery);
    }

    public String getAIResponseWithContext(String userQuery, String chatSessionId) throws Exception {
        String intent = openAIService.classifyIntent(userQuery);
        List<ChatMessage> conversationHistory = redisService.getChatSessionMessages(chatSessionId);
        List<Map<String, String>> formattedHistory = conversationHistory.stream()
                .filter(msg -> !msg.getSender().equals("Bot"))  // Only keep user messages
                .map(msg -> Map.of(
                        "role", "user",  // All messages will be from the user
                        "content", msg.getContent()
                ))
                .collect(Collectors.toList());

        String context = getKnowledgeBase(userQuery);

        List<Map<String, String>> messages = new ArrayList<>();

        // Step 1: Add the system prompt
        messages.add(Map.of(
                "role", "system",
                "content", PROMPT
        ));

        // Step 2: Add the conversation history (formattedHistory) to messages
        messages.addAll(formattedHistory);

        // Step 3: Add the retrieved context
        messages.add(Map.of(
                "role", "assistant",
                "content", context
        ));

        // Step 4: Add the latest user query
        messages.add(Map.of(
                "role", "user",
                "content",  userQuery
        ));

        return openAIService.getChatCompletion(messages);
    }

    public String handleSummarization(String chatSessionId) throws Exception {
        List<ChatMessage> conversationHistory = redisService.getChatSessionMessages(chatSessionId);
        List<Map<String, String>> formattedHistory = conversationHistory.stream()
                .map(msg -> Map.of(
                        "role", msg.getSender().equals("Bot") ? "assistant" : "user",
                        "content", msg.getContent()
                ))
                .collect(Collectors.toList());


        List<Map<String, String>> messages = new ArrayList<>();

        // Step 1: Add the system prompt
        messages.add(Map.of(
                "role", "system",
                "content", PROMPT
        ));

        // Step 2: Add the conversation history (formattedHistory) to messages
        messages.addAll(formattedHistory);

        // Step 4: Add the latest user query
        messages.add(Map.of(
                "role", "user",
                "content", "總結我們的對話記錄"
        ));

        return openAIService.getChatCompletion(messages);
    }

    public String handleHandOver(String chatSessionId) throws Exception {
        List<ChatMessage> conversationHistory = redisService.getChatSessionMessages(chatSessionId);
        List<Map<String, String>> formattedHistory = conversationHistory.stream()
                .map(msg -> Map.of(
                        "role", msg.getSender().equals("Bot") ? "assistant" : "user",
                        "content", msg.getContent()
                ))
                .collect(Collectors.toList());


        List<Map<String, String>> messages = new ArrayList<>();

        // Step 1: Add the system prompt
        messages.add(Map.of(
                "role", "system",
                "content", PROMPT
        ));

        // Step 2: Add the conversation history (formattedHistory) to messages
        messages.addAll(formattedHistory);

        // Step 4: Add the latest user query
        messages.add(Map.of(
                "role", "user",
                "content", "簡要闡述用戶遇到的問題與你提供的解決方案。高亮強調用戶仍然需要解決的問題。這些資訊將會幫助接手你工作的客服快速了解情況，言簡意賅。"
        ));

        return openAIService.getChatCompletion(messages);
    }

    private String getKnowledgeBase(String userQuery) throws IOException {
        float[] queryVector = openAIService.getEmbedding(userQuery);
        List<List<SearchResp.SearchResult>> searchResults = milvusService.searchInMilvus(queryVector);
        String context = searchResults.stream()
                .flatMap(List::stream)
                .map(result -> {
                    String content = result.getEntity().get("content").toString();
                    String answer = result.getEntity().get("answer") != null ? result.getEntity().get("answer").toString() : "";
                    return content + " " + answer;
                })
                .collect(Collectors.joining(" "));
        log.error("Context" + context);
        return context;
    }

    public String handleQueryBasedOnIntent(String intent, String query, String chatSessionId) throws Exception {
        switch (intent) {
            case "總結":
                return handleSummarization(chatSessionId);
            case "忘記密碼":
                return "您是否需要<a href = '/account_forget.html'>忘記密碼</a>";
            case "重設密碼":
                return "您是否需要<a href = '/account_reset.html'>重設密碼</a>";
            case "人工客服":
                return "您是否需要轉接人工客服？<button onclick='requestHumanSupport()'>轉接人工客服</button>";
            case "獲取資訊":
                return getAIResponseWithContext(query, chatSessionId);
            default:
                return "抱歉，我無法理解您的請求。";
        }
    }

    public String twoStageResponse(String query, String chatSessionId) throws Exception {
        // get intent
        String intent = openAIService.classifyIntent(query);
        // handle based on intent
        return handleQueryBasedOnIntent(intent, query, chatSessionId);
    }
}

package com.twm.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.twm.bot.model.ChatMessage;
import com.twm.bot.model.faq.FAQ;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;


@Log4j2
@Service
public class ElasticsearchService {

    private static final String FAQ_INDEX = "faq_index";
    private static final String CHAT_INDEX = "chat_messages_index"; // New index for chat messages


    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String ELASTICSEARCH_URL;
    private final String ELASTICSEARCH_USERNAME;
    private final String ELASTICSEARCH_PASSWORD;

    @Autowired
    public ElasticsearchService(RestTemplate restTemplate, ObjectMapper objectMapper,
                                @Value("${elasticsearch.host}") String elasticsearchUrl,
                                @Value("${elasticsearch.username}") String elasticsearchUsername,
                                @Value("${elasticsearch.password}") String elasticsearchPassword) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.ELASTICSEARCH_URL = elasticsearchUrl;
        this.ELASTICSEARCH_USERNAME = elasticsearchUsername;
        this.ELASTICSEARCH_PASSWORD = elasticsearchPassword;
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");

        String auth = ELASTICSEARCH_USERNAME + ":" + ELASTICSEARCH_PASSWORD;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        headers.add("Authorization", "Basic " + encodedAuth);

        return headers;
    }

    // Helper function to handle Elasticsearch requests
    private JsonNode handleRequest(String endpoint, String queryJson, HttpMethod method, String index) throws Exception {
        String url = ELASTICSEARCH_URL + "/" + index + endpoint;
        log.info(url);
        HttpHeaders headers = createHeaders();

        HttpEntity<String> request = new HttpEntity<>(queryJson, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, method, request, String.class);

        return objectMapper.readTree(response.getBody());
    }

    public JsonNode searchFAQs(String queryJson) throws Exception {
        return handleRequest("/_search", queryJson, HttpMethod.POST, FAQ_INDEX);
    }

    public String indexFAQ(FAQ faq) throws Exception {
        String faqJson = objectMapper.writeValueAsString(faq);
        JsonNode responseBody = handleRequest("/_doc", faqJson, HttpMethod.POST, FAQ_INDEX);
        return responseBody.path("_id").asText(); // Return the generated document ID
    }

    public JsonNode sendBulkRequest(String bulkRequestBody) throws Exception {
        String url = ELASTICSEARCH_URL + "/_bulk";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/x-ndjson; charset=UTF-8"); // UTF-8 is important for Chinese character encoding
        headers.add("Authorization", createHeaders().getFirst("Authorization"));

        HttpEntity<String> request = new HttpEntity<>(bulkRequestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        log.info("Bulk request response: {}", response.getBody());
        return objectMapper.readTree(response.getBody());
    }


    public void deleteFAQ(String documentId) throws Exception {
        handleRequest("/_doc/" + documentId, null, HttpMethod.DELETE, FAQ_INDEX);
    }

    public FAQ getFAQ(String documentId) throws Exception {
        JsonNode jsonNode = handleRequest("/_doc/" + documentId, null, HttpMethod.GET, FAQ_INDEX);
        return objectMapper.treeToValue(jsonNode.get("_source"), FAQ.class);
    }

    public Map<String, String> getRandomFAQMap(int count) throws Exception {
        // Elasticsearch query for getting random documents
        String queryJson = String.format(
                "{ " +
                        "  \"size\": %d, " +
                        "  \"query\": { " +
                        "    \"function_score\": { " +
                        "      \"query\": { \"match_all\": {} }, " +
                        "      \"random_score\": {} " +
                        "    } " +
                        "  } " +
                        "}", count
        );

        JsonNode responseBody = handleRequest("/_search", queryJson, HttpMethod.POST, FAQ_INDEX);
        JsonNode hits = responseBody.path("hits").path("hits");

        Map<String, String> faqMap = new HashMap<>();
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                FAQ faq = objectMapper.treeToValue(hit.path("_source"), FAQ.class);
                faqMap.put(faq.getQuestion(), faq.getAnswer());
            }
        }
        return faqMap;
    }

    public String saveChatMessage(long userId, ChatMessage chatMessage) throws Exception {
        chatMessage.setTimestamp(chatMessage.getTimestamp() != null ? chatMessage.getTimestamp() : Instant.now());
        String chatMessageJson = objectMapper.writeValueAsString(chatMessage);
        JsonNode chatMessageNode = objectMapper.readTree(chatMessageJson);
        ((ObjectNode) chatMessageNode).put("userId", String.valueOf(userId));

        String finalChatMessageJson = chatMessageNode.toString();
        JsonNode responseBody = handleRequest("/_doc", finalChatMessageJson, HttpMethod.POST, CHAT_INDEX);
        return responseBody.path("_id").asText();
    }

    public ChatMessage findLatestMessageByUserId(long userId) throws Exception {
        String queryJson = String.format(
                "{ \"query\": { \"match\": { \"userId\": \"%s\" } }, \"sort\": [{ \"timestamp\": { \"order\": \"desc\" }}], \"size\": 1 }",
                String.valueOf(userId)
        );
        JsonNode responseBody = handleRequest("/_search", queryJson, HttpMethod.POST, CHAT_INDEX);
        JsonNode hits = responseBody.path("hits").path("hits");

        if (hits.isArray() && hits.size() > 0) {
            return objectMapper.treeToValue(hits.get(0).path("_source"), ChatMessage.class);
        } else {
            return null;
        }
    }
    public List<ChatMessage> findAllMessagesInSession(long userId, String chatSessionId) throws Exception {
        String queryJson = String.format(
                "{ \"query\": { \"bool\": { \"must\": [{ \"match\": { \"userId\": \"%s\" }}, { \"match\": { \"chatSessionId\": \"%s\" }}] } }, \"sort\": [{ \"timestamp\": { \"order\": \"asc\" }}] }",
                String.valueOf(userId), chatSessionId
        );
        JsonNode responseBody = handleRequest("/_search", queryJson, HttpMethod.POST, CHAT_INDEX);
        JsonNode hits = responseBody.path("hits").path("hits");

        List<ChatMessage> messages = new ArrayList<>();
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                ChatMessage message = objectMapper.treeToValue(hit.path("_source"), ChatMessage.class);
                messages.add(message);
            }
        }
        return messages;
    }

    public String findLatestSessionByUserId(long userId) throws Exception {
        String queryJson = String.format(
                "{ \"query\": { \"match\": { \"userId\": \"%s\" } }, \"sort\": [{ \"timestamp\": { \"order\": \"desc\" }}], \"size\": 1 }",
                String.valueOf(userId)
        );

        JsonNode responseBody = handleRequest("/_search", queryJson, HttpMethod.POST, CHAT_INDEX);
        JsonNode hits = responseBody.path("hits").path("hits");

        if (hits.isArray() && hits.size() > 0) {
            return hits.get(0).path("_source").path("chatSessionId").asText();
        } else {
            return null;
        }
    }

    public List<ChatMessage> findLatestMessagesByUserId(long userId) throws Exception {
        // Find the latest session ID for the user
        String latestSessionId = findLatestSessionByUserId(userId);
        if (latestSessionId == null) {
            // If no session is found, return an empty list
            return new ArrayList<>();
        }

        // Retrieve all messages for that session
        return findAllMessagesInSession(userId, latestSessionId);
    }

}
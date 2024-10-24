package com.twm.bot.service;

import lombok.extern.log4j.Log4j2;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@Service
public class OpenAIService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public float[] getEmbedding(String text) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.openai.com/v1/embeddings");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("input", text);
            requestBody.put("model", "text-embedding-3-small");

            String json = objectMapper.writeValueAsString(requestBody);
            request.setEntity(new StringEntity(json));
            request.setHeader("Authorization", "Bearer " + apiKey);
            request.setHeader("Content-Type", "application/json");

            try (var response = httpClient.execute(request)) {
                String responseBody = new String(response.getEntity().getContent().readAllBytes());
                JsonNode embeddingNode = objectMapper.readTree(responseBody)
                        .path("data").get(0)
                        .path("embedding");

                // Check if embeddingNode is null
                if (embeddingNode == null) {
                    throw new RuntimeException("No embedding found in the OpenAI response");
                }

                return objectMapper.convertValue(embeddingNode, float[].class);
            }
        }
    }

    public String getChatCompletion(String prompt, String context, String query) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.openai.com/v1/chat/completions");
            String json = objectMapper.writeValueAsString(Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", prompt
                            ),
                            Map.of(
                                    "role", "assistant",
                                    "content", context
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", query
                            )
                    )
            ));
            request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON.withCharset("UTF-8")));
            request.setHeader("Authorization", "Bearer " + apiKey);
            request.setHeader("Content-Type", "application/json;charset=UTF-8");

            try (var response = httpClient.execute(request)) {
                String responseBody = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                // Log the response body for debugging
                log.info("OpenAI Response: " + responseBody);

                return objectMapper.readTree(responseBody)
                        .path("choices").get(0)
                        .path("message").path("content").asText();
            }
        }
    }

    public String getChatCompletion(List<Map<String, String>> messages) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.openai.com/v1/chat/completions");
            String json = objectMapper.writeValueAsString(Map.of(
                    "model", "gpt-4o-mini",
                    //"temperature",0.6,
                    "seed",19980604,
                    "messages", messages
            ));
            request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON.withCharset("UTF-8")));
            request.setHeader("Authorization", "Bearer " + apiKey);
            request.setHeader("Content-Type", "application/json;charset=UTF-8");

            try (var response = httpClient.execute(request)) {
                String responseBody = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                // Log the response body for debugging
                log.info("OpenAI Response: " + responseBody);

                return objectMapper.readTree(responseBody)
                        .path("choices").get(0)
                        .path("message").path("content").asText();
            }
        }
    }

    public String classifyIntent(String userQuery) throws Exception {
        String prompt = userQuery;

        // Prepare the messages for OpenAI API
        String jsonRequest = objectMapper.writeValueAsString(Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an intent classifier.You will classify user intent by context, not by keyword alone.intent should be one of these categories: '總結', '忘記密碼', '重設密碼', or '人工客服', or '獲取資訊'.Response format: intent: <one of the categories>"),
                        Map.of("role", "user", "content", prompt)
                )
        ));

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.openai.com/v1/chat/completions");
            request.setHeader("Authorization", "Bearer " + apiKey);
            request.setHeader("Content-Type", "application/json;charset=UTF-8");
            request.setEntity(new StringEntity(jsonRequest, ContentType.APPLICATION_JSON.withCharset("UTF-8")));

            var response = httpClient.execute(request);
            String responseBody = new String(response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);

            // Parse the response
            JsonNode rootNode = objectMapper.readTree(responseBody);
            String intentResponse = rootNode.path("choices").get(0).path("message").path("content").asText();

            // Extract intent from the response
            String intent = intentResponse.split("intent: ")[1].trim();
            return intent;
        }
    }
}
package com.twm.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twm.bot.model.faq.FAQ;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Log4j2
public class SearchService {

    private final ElasticsearchService elasticsearchService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SearchService(ElasticsearchService elasticsearchService, ObjectMapper objectMapper) {
        this.elasticsearchService = elasticsearchService;
        this.objectMapper = objectMapper;
    }

    public List<FAQ> autocompleteFAQs(String prefix) throws Exception {
        // Create the JSON query for prefix-based autocomplete
        String queryJson = "{ " +
                "\"suggest\": { " +
                "\"question-suggest\": { " +
                "\"prefix\": \"" + prefix + "\", " +
                "\"completion\": { " +
                "\"field\": \"question.suggest\", " +
                "\"size\": 5 " +
                "} " +
                "} " +
                "} " +
                "}";

        JsonNode searchResult = elasticsearchService.searchFAQs(queryJson);
        List<FAQ> faqs = new ArrayList<>();

        JsonNode options = searchResult.path("suggest").path("question-suggest").get(0).path("options");
        if (options.isArray()) {
            for (JsonNode option : options) {
                FAQ faq = objectMapper.treeToValue(option.path("_source"), FAQ.class);
                faqs.add(faq);
            }
        }

        return faqs;
    }

    public List<FAQ> partialMatchFAQs(String searchTerm) throws Exception {
        // Create the JSON query for n-gram based partial matching
        String queryJson = "{ " +
                "\"query\": { " +
                "\"match\": { " +
                "\"question.ngram\": \"" + searchTerm + "\" " +
                "} " +
                "} " +
                "}";

        JsonNode searchResult = elasticsearchService.searchFAQs(queryJson);
        List<FAQ> faqs = new ArrayList<>();

        JsonNode hits = searchResult.path("hits").path("hits");
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                FAQ faq = objectMapper.treeToValue(hit.path("_source"), FAQ.class);
                faqs.add(faq);
            }
        }

        return faqs;
    }

    public String indexFAQ(FAQ faq) throws Exception {
        return elasticsearchService.indexFAQ(faq);
    }

    public List<String> bulkIndexFAQs(List<FAQ> faqs) throws Exception {
        StringBuilder bulkRequestBody = new StringBuilder();

        for (FAQ faq : faqs) {
            bulkRequestBody.append("{ \"index\": { \"_index\": \"faq_index\" } }\n");
            // Convert the FAQ object to JSON and append it
            String faqJson = objectMapper.writeValueAsString(faq);
            bulkRequestBody.append(faqJson).append("\n");
        }

        // Send the bulk request using ElasticsearchService
        JsonNode response = elasticsearchService.sendBulkRequest(bulkRequestBody.toString());

        // Extract and return the list of generated document IDs
        List<String> ids = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            ids.add(item.path("index").path("_id").asText());
        }

        return ids;
    }



    public void deleteFAQ(String documentId) throws Exception {
        elasticsearchService.deleteFAQ(documentId);
    }

    public FAQ getFAQ(String documentId) throws Exception {
        return elasticsearchService.getFAQ(documentId);
    }

    public Optional<FAQ> exactMatchFAQ(String searchTerm) throws Exception {
        String queryJson = "{ " +
                "\"query\": { " +
                "\"query_string\": { " +
                "\"default_field\": \"question\", " +
                "\"query\": \"\\\"" + searchTerm + "\\\"\" " +
                "} " +
                "} " +
                "}";
        JsonNode searchResult = elasticsearchService.searchFAQs(queryJson);
        JsonNode hits = searchResult.path("hits").path("hits");
        if (hits.isArray() && hits.size() > 0) {
            FAQ faq = objectMapper.treeToValue(hits.get(0).path("_source"), FAQ.class);
            return Optional.of(faq);
        } else {
            return Optional.empty();
        }
    }
}
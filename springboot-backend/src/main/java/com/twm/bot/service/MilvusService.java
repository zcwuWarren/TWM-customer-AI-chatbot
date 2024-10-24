package com.twm.bot.service;

import io.milvus.exception.MilvusException;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.request.data.FloatVec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Log4j2
@Service
public class MilvusService {

    private MilvusClientV2 client;
    private final String COLLECTION = "knowledge_base_dynamic";

    @Value("${milvus.host}")
    private String host;

    @PostConstruct
    public void init() {
        ConnectConfig config = ConnectConfig.builder()
                .uri(host)
                .build();
        client = new MilvusClientV2(config);
        try {
            LoadCollectionReq loadReq = LoadCollectionReq.builder()
                    .collectionName(COLLECTION)
                    .build();
            client.loadCollection(loadReq);
        } catch (MilvusException e) {
            log.error("Failed to load Milvus collection: " + e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        client.close();
    }

    public List<List<SearchResp.SearchResult>> searchInMilvus(float[] queryVector) {
        SearchResp searchR = client.search(SearchReq.builder()
                .collectionName(COLLECTION)
                .annsField("embedding")
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(5)
                .outputFields(Arrays.asList("content", "answer"))
                .build());
        List<List<SearchResp.SearchResult>> searchResults = searchR.getSearchResults();
        return searchResults;
    }
}
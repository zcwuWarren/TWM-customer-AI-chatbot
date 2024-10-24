package com.twm.bot.controller;

import com.twm.bot.data.dto.SearchResponse;
import com.twm.bot.model.faq.FAQ;
import com.twm.bot.service.SearchService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Log4j2
@RestController
@RequestMapping("/api/faq")
public class FAQController {

    private final SearchService searchService;

    @Autowired
    public FAQController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public ResponseEntity<?> createFAQ(@RequestBody FAQ faq) throws Exception {
            return ResponseEntity.ok(new SearchResponse<>(searchService.indexFAQ(faq)));
    }

    @PostMapping("/batch")
    public ResponseEntity<?>  batchAddFAQs(@RequestBody List<FAQ> faqs) throws Exception {
        return ResponseEntity.ok(new SearchResponse<>(searchService.bulkIndexFAQs(faqs)));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<?>  getFAQ(@PathVariable String documentId) throws Exception {
        return ResponseEntity.ok(new SearchResponse<>(searchService.getFAQ(documentId)));
    }

    @DeleteMapping("/{documentId}")
    public void deleteFAQ(@PathVariable String documentId) throws Exception {
        searchService.deleteFAQ(documentId);
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<?>  autocompleteFAQs(@RequestParam String prefix) throws Exception {
        return ResponseEntity.ok(new SearchResponse<>(searchService.autocompleteFAQs(prefix)));
    }

    @GetMapping("/search")
    public ResponseEntity<?>  partialMatchFAQs(@RequestParam String keyword) throws Exception {
        return ResponseEntity.ok(new SearchResponse<>(searchService.partialMatchFAQs(keyword)));

    }
}
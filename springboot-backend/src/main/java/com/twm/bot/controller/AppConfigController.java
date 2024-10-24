package com.twm.bot.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class AppConfigController {

    @Value("${app.base-url}")
    private String baseUrl;

    @GetMapping("/base-url")
    public ResponseEntity<String> getBaseUrl() {
        return new ResponseEntity<>(baseUrl, HttpStatus.OK);
    }
}

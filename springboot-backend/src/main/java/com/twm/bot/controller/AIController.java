package com.twm.bot.controller;

import com.twm.bot.service.CustomerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private CustomerService customerService;

    public AIController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/query")
    public String handleQuery(@RequestBody String query) throws Exception {
        return customerService.getAIResponse(query);
    }
}
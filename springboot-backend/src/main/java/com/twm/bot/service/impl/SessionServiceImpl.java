package com.twm.bot.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twm.bot.middleware.JwtTokenUtil;
import com.twm.bot.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class SessionServiceImpl implements SessionService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final JwtTokenUtil jwtTokenUtil;
    private static final long SESSION_TIMEOUT_MINUTES = 30;

    @Autowired
    public SessionServiceImpl(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper, JwtTokenUtil jwtTokenUtil) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Override
    public void storeSessionData(String jwtToken) throws JsonProcessingException {
        String sessionKey = "session:" + jwtToken;  // Use a single key for the entire session

        // Serialize and store user details in a Redis hash
        String userJson = objectMapper.writeValueAsString(jwtTokenUtil.getUserFromToken(jwtToken));
        redisTemplate.opsForHash().put(sessionKey, "user", userJson);

        // Set a TTL for the entire session
        redisTemplate.expire(sessionKey, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public void refreshSession(String jwtToken) {
        String sessionDataString = redisTemplate.opsForValue().get(jwtToken);

        if (sessionDataString == null) {
            throw new RuntimeException("Session not found or expired");
        }

        try {
            // Refresh the session by updating the TTL
            redisTemplate.opsForValue().set(jwtToken, sessionDataString, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh session data", e);
        }
    }

    @Override
    public void deleteSession(String jwtToken) {
        redisTemplate.delete(jwtToken);
    }
}
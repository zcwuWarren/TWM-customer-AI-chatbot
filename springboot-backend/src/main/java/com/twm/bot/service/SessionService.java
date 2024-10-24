package com.twm.bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface SessionService {
    void storeSessionData(String jwtToken) throws JsonProcessingException;
    void refreshSession(String jwtToken);
    void deleteSession(String jwtToken);
}

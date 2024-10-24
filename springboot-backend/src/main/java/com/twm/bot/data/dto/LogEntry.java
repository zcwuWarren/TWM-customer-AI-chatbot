package com.twm.bot.data.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class LogEntry {
    private Timestamp timestamp;
    private String message;

    public LogEntry(Timestamp timestamp, String message) {
        this.timestamp = timestamp;
        this.message = message;
    }

    public LogEntry(String message) {
        this.timestamp = new Timestamp(System.currentTimeMillis());
        this.message = message;
    }
}

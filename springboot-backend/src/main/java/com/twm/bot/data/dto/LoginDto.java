package com.twm.bot.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.twm.bot.model.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class LoginDto {
    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("access_expired")
    private long accessExpired;

    @JsonProperty("user")
    private User user;

    // for term acceptance check
    @JsonProperty("term")
    private boolean term;
}

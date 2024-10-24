package com.twm.bot.service;

import com.twm.bot.data.dto.LoginDto;
import com.twm.bot.exception.auth.AuthServiceExcption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OauthService {

    private final LoginService loginService;
    private String redirectUri;
    private final String clientId = "appworks";
    private final String clientSecret = "FK3i46wSuObFXr5ezVfYgJlT3/KtOIn2uw/5Fh9nXAaDoOcHElB/gKy5URr7SKkG";
    private final String tokenUrl = "https://stage.oauth.taiwanmobile.com/MemberOAuth/getAccessToken";
    private final String profileUrl = "https://stage.oauth.taiwanmobile.com/MemberOAuth/getUserProfile";

    public OauthService(LoginService loginService,
                        @Value("${app.base-url}") String baseUrl) {
        this.loginService = loginService;
        this.redirectUri = baseUrl + "/account_login.html";
    }

    public LoginDto getToken(Map<String, String> payload) {

        String code = payload.get("code");

        // Prepare token request
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("redirect_uri", redirectUri);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, clientSecret);

        try {
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // Request access token (getAccessToken API)
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> tokenResponse = restTemplate.exchange(tokenUrl, HttpMethod.POST, request, Map.class);
            String accessToken = (String) tokenResponse.getBody().get("access_token");

            Map<String, String> response = new HashMap<>();
            response.put("access_token", accessToken);

            // get User Profile (getUserProfile API)
            HttpHeaders profileHeaders = new HttpHeaders();
            profileHeaders.setBearerAuth(accessToken);

            HttpEntity<Void> profileRequest = new HttpEntity<>(profileHeaders);
            ResponseEntity<Map> profileResponse = restTemplate.exchange(profileUrl, HttpMethod.POST, profileRequest, Map.class);

            String email = (String) profileResponse.getBody().get("email");

            // Save user to database
            LoginDto loginDto = loginService.userLoginTWM(email);
            Map<String, String> responseFromLoginService = new HashMap<>();
            responseFromLoginService.put("jwtToken", loginDto.getAccessToken());
            return loginDto;
        } catch (Exception e) {
            throw new AuthServiceExcption("Failed to get access token or user profile", e);
        }
    }
}

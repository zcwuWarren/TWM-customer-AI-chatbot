package com.twm.bot.controller;

import com.twm.bot.data.dto.LoginDto;
import com.twm.bot.data.form.LoginForm;
import com.twm.bot.data.form.RegistrationForm;
import com.twm.bot.data.form.ForgotPasswordForm;
import com.twm.bot.data.form.ResetPasswordForm;
import com.twm.bot.exception.auth.AuthLoginFailException;
import com.twm.bot.service.OauthService;
import com.twm.bot.service.UserService;
import com.twm.bot.model.user.User;
import com.twm.bot.service.LoginService;
import com.twm.bot.service.CaptchaService;
import com.twm.bot.service.impl.LoginServiceImpl;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${app.base-url}")
    private String baseUrl;

    private String redirectUri;
    private final UserService userService;
    private final CaptchaService captchaService;
    private final LoginService loginService;
    private final OauthService oauthService;
    private final String clientId = "appworks";
    private final String authUrl = "https://stage.oauth.taiwanmobile.com/MemberOAuth/authPageLogin";

    public AuthController(UserService userService, CaptchaService captchaService, LoginService loginService, LoginServiceImpl loginServiceImpl, OauthService oauthService) {
        this.userService = userService;
        this.captchaService = captchaService;
        this.loginService = loginService;
        this.oauthService = oauthService;
    }

    @PostConstruct
    public void init() {
        this.redirectUri = baseUrl + "/account_login.html";
    }

    /**
     * 用戶註冊
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegistrationForm form) {
        @SuppressWarnings("unused")
        User registeredUser = userService.registerUser(form);
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    /**
     * 獲取驗證碼
     */
    @GetMapping("/captcha")
    public ResponseEntity<?> getCaptcha() {
        Map<String, Object> captchaData = captchaService.generateCaptcha();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(captchaData);
    }

    /**
     * 忘記密碼
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordForm form) {
        try {
            userService.processForgotPassword(form);
            return ResponseEntity.ok(Map.of("message", "重置密碼的郵件已發送"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginForm loginForm) {
        LoginDto logindto = loginService.userLoginNative(loginForm.getEmail(), loginForm.getPassword(), loginForm.getCaptchaId(), loginForm.getCaptcha());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("data", logindto));
    }

    @PostMapping("/account_reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordForm form) {
        try {
            String token = form.getToken();
            userService.validatePasswordResetToken(token);
            userService.updateNewPassword(token, form);
            return ResponseEntity.ok(Map.of("message", "密碼已重置"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // TWM oauth login
    // redirect to OAuth Authorization Page
    @GetMapping("/TWM_oauth")
    public void redirectToAuth(HttpServletResponse response) throws IOException {
        try {
            // authPageLogin API
            String authRequest = authUrl + "?response_type=code&client_id=" + clientId + "&redirect_uri=" + redirectUri;
            response.sendRedirect(authRequest);
        } catch (Exception e) {
            throw new AuthLoginFailException("Failed to redirect to OAuth", e);
        }
    }

    @PostMapping("/exchangeToken")
    public ResponseEntity<?> exchangeTwnToken(@RequestBody Map<String, String> payload) {

        LoginDto loginDto = oauthService.getToken(payload);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("data", loginDto));
    }

    @PostMapping("updateTerms")
    public ResponseEntity<?> updateTerms(@RequestBody Map<String, String> requestBody) {
        // retrieve accessToken from requestBody
        String accessToken = requestBody.get("accessToken");

        loginService.updateUserTerms(accessToken);
        return ResponseEntity.ok(Map.of("message", "已簽署授權書"));
    }
}

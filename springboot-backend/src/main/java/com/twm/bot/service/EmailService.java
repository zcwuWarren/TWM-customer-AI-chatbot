package com.twm.bot.service;

import com.twm.bot.model.PasswordResetToken;
import com.twm.bot.model.user.User;
import com.twm.bot.repository.PasswordResetTokenRepository;
import com.twm.bot.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final String baseUrl;
    private final String mailFrom;

    public EmailService(JavaMailSender mailSender,
                        PasswordResetTokenRepository tokenRepository,
                        UserRepository userRepository,
                        @Value("${app.base-url}") String baseUrl,
                        @Value("${spring.mail.username}") String mailFrom) {
        this.mailSender = mailSender;
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.baseUrl = baseUrl;
        this.mailFrom = mailFrom;
    }

    public void sendPasswordResetEmail(String to) {
        // 根據 email 查找用戶
        User user = userRepository.findByEmail(to)
                .orElseThrow(() -> new IllegalArgumentException("No user found with email: " + to));

        // 生成並保存重置 Token
        String resetToken = generateResetToken();
        saveResetToken(resetToken, user);

        // 發送郵件
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@twmno1.site");  // 使用你在 SES 中驗證的域名
        message.setTo(to);
        message.setSubject("密碼重置");
        message.setText("請點擊以下鏈接重置您的密碼：\n"
                + baseUrl + "/account_reset.html?token=" + resetToken);

        mailSender.send(message);
    }

    private String generateResetToken() {
        return UUID.randomUUID().toString();
    }

    private void saveResetToken(String token, User user) {
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);  // 正確設置 user 對象
        resetToken.setCreatedAt(LocalDateTime.now());
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(24)); // 設定 token 有效期為 24 小時
        tokenRepository.save(resetToken);
    }
}

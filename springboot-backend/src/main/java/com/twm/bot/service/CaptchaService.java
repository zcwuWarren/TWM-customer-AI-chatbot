package com.twm.bot.service;

import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class CaptchaService {
    private final RedisService redisService;
    private final Random random = new Random();
    private static final Duration CAPTCHA_EXPIRATION = Duration.ofMinutes(5);

    public CaptchaService(RedisService redisService) {
        this.redisService = redisService;
    }

    public Map<String, Object> generateCaptcha() {
        String captchaText = generateCaptchaText();
        String captchaId = java.util.UUID.randomUUID().toString();

        redisService.saveCaptcha(captchaId, captchaText, CAPTCHA_EXPIRATION);

        BufferedImage image = createCaptchaImage(captchaText);
        String base64Image = convertToBase64(image);

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("captchaImage", base64Image);
        return result;
    }

    private String generateCaptchaText() {
        return String.format("%04d", random.nextInt(10000));
    }

    private BufferedImage createCaptchaImage(String text) {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();

        // 設置背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        // 繪製干擾線
        g.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i < 20; i++) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            int xl = random.nextInt(12);
            int yl = random.nextInt(12);
            g.drawLine(x, y, x + xl, y + yl);
        }

        // 繪製驗證碼
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 24));
        for (int i = 0; i < text.length(); i++) {
            g.drawString(String.valueOf(text.charAt(i)), 20 + i * 20, 30);
        }

        g.dispose();
        return image;
    }

    private String convertToBase64(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error converting image to Base64", e);
        }
    }

    public boolean isCaptchaValid(String captchaId, String userInput) {
        String storedCaptcha = redisService.getCaptcha(captchaId);

        // 無論驗證成功與否，都刪除驗證碼
        redisService.deleteCaptcha(captchaId);

        return storedCaptcha != null && storedCaptcha.equals(userInput);
    }
}
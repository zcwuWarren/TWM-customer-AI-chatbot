package com.twm.bot.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twm.bot.data.dto.LogEntry;
import com.twm.bot.data.dto.LoginDto;
import com.twm.bot.exception.auth.UserNotExistException;
import com.twm.bot.exception.auth.UserPasswordMismatchException;
import com.twm.bot.middleware.JwtTokenUtil;
import com.twm.bot.model.user.User;
import com.twm.bot.repository.user.UserRepository;
import com.twm.bot.service.CaptchaService;
import com.twm.bot.service.LoginService;
import com.twm.bot.service.SessionService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Log4j2
@Service
public class LoginServiceImpl implements LoginService {
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;
    private final SessionService sessionService;
    private final String dafaultPasswordForTwmLogin = "TWM";
    private final CaptchaService captchaService;

    private static final long SESSION_TIMEOUT_MINUTES = 30;

    @Autowired
    public LoginServiceImpl(UserRepository userRepository, RedisTemplate<String, String> redisTemplate,
                            ObjectMapper objectMapper,
                            PasswordEncoder passwordEncoder, JwtTokenUtil jwtTokenUtil, SessionService sessionService, CaptchaService captchaService) {
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenUtil = jwtTokenUtil;
        this.sessionService = sessionService;
        this.captchaService = captchaService;
    }

    @Override
    public LoginDto userLoginNative(String email, String password, String captchaId, String captcha) {
        if (!captchaService.isCaptchaValid(captchaId, captcha)) {
            throw new RuntimeException("Invalid captcha");
        }

        Optional<User> optionalUser = userRepository.findByEmail(email);

        // Check if user exists
        User user = optionalUser.orElseThrow(() ->
                new UserNotExistException("賬號或密碼錯誤")
        );

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UserPasswordMismatchException("賬號或密碼錯誤");
        }
        String jwtToken = jwtTokenUtil.generateToken(user);
        Long accessExpired = jwtTokenUtil.getExpirationDateFromToken(jwtToken).getTime();

        try {
            sessionService.storeSessionData(jwtToken);
        } catch (JsonProcessingException e) {
            log.error("Failed to store user session data for user: {}", user.getEmail(), e);
            throw new RuntimeException("Fail to save user session, login fails");
        }

        // check term acceptance
        boolean term = user.getTermsAcceptedDate() != null;
        LoginDto loginDto = new LoginDto(jwtToken, accessExpired, user, term);
        log.info("logged in:{}", loginDto);
        return loginDto;
    }

    @Override
    public void logOut(String jwtToken) {
        sessionService.deleteSession(jwtToken);
    }

    @Override
    public LoginDto userLoginTWM(String email) {
        // Check if user exists
        Optional<User> optionalUser = userRepository.findByEmail(email);
        User user;

        if (optionalUser.isEmpty()) {
            // If first-time login, create a new user
            user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(dafaultPasswordForTwmLogin)); // Set default password
            user.setProvider(User.Provider.TAIWAN_MOBILE);
            userRepository.save(user);
        } else {
            // If user exists, retrieve user
            user = optionalUser.get();
        }

        // Generate JWT token
        String jwtToken = jwtTokenUtil.generateToken(user);
        Long accessExpired = jwtTokenUtil.getExpirationDateFromToken(jwtToken).getTime();

        try {
            sessionService.storeSessionData(jwtToken);
        } catch (JsonProcessingException e) {
            log.error("Failed to store user session data for user: {}", user.getEmail(), e);
            throw new RuntimeException("Fail to save user session, login fails");
        }

        // check term acceptance
        boolean term = user.getTermsAcceptedDate() != null;
        LoginDto loginDto = new LoginDto(jwtToken, accessExpired, user, term);
        log.info("logged in via TWM OAuth:{}", loginDto);
        return loginDto;
    }

    @Override
    public User updateUserTerms(String accessToken) {
        // extract userId from accessToken
        Long userId = jwtTokenUtil.getUserFromToken(accessToken).getId();

        //Check if user exists
        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("not existed user");
        }
            User user = optionalUser.get();
            user.setTermsAcceptedDate(new Timestamp(System.currentTimeMillis()));
            return userRepository.save(user);
    }
}

package com.twm.bot.service;

import com.twm.bot.data.form.ForgotPasswordForm;
import com.twm.bot.data.form.RegistrationForm;
import com.twm.bot.data.form.ResetPasswordForm;
import com.twm.bot.model.PasswordResetToken;
import com.twm.bot.model.user.User;
import com.twm.bot.repository.PasswordResetTokenRepository;
import com.twm.bot.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, CaptchaService captchaService, EmailService emailService, PasswordResetTokenRepository passwordResetTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.captchaService = captchaService;
        this.emailService = emailService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Transactional
    public User registerUser(RegistrationForm form) {
        if (!captchaService.isCaptchaValid(form.getCaptchaId(), form.getCaptcha())) {
            throw new RuntimeException("Invalid captcha");
        }
        if (userRepository.existsByEmail(form.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setEmail(form.getEmail());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setProvider(User.Provider.valueOf(form.getProvider().toUpperCase()));
        user.setRegisterAt(new Timestamp(System.currentTimeMillis()));
        user.addRoles("user");

        return userRepository.save(user);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public void processForgotPassword(ForgotPasswordForm form) {
        if (!captchaService.isCaptchaValid(form.getCaptchaId(), form.getCaptcha())) {
            throw new RuntimeException("Invalid captcha");
        }

        if (!userRepository.existsByEmail(form.getMainAccount())) {
            throw new RuntimeException("Main account does not exist");
        }

        String emailToSend = form.getMainAccount();

        emailService.sendPasswordResetEmail(emailToSend);
    }

    public String validatePasswordResetToken(String token) {
        Optional<PasswordResetToken> resetToken = passwordResetTokenRepository.findByToken(token);
        if (!resetToken.isPresent()) {
            return "Invalid token";
        }
        PasswordResetToken passwordResetToken = resetToken.get();
        if (passwordResetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "Expired token";
        } return "Valid";
    }

    public User updateNewPassword(String token, ResetPasswordForm form) {
        if (!captchaService.isCaptchaValid(form.getCaptchaId(), form.getCaptcha())) {
            throw new RuntimeException("Invalid captcha");
        }

        // hash the new password
        String newPassword = passwordEncoder.encode(form.getNewPassword());
        // check resetToken existence
        Optional<PasswordResetToken> resetToken = passwordResetTokenRepository.findByToken(token);
        if (resetToken.isPresent()) {
            PasswordResetToken passwordResetToken = resetToken.get();
            // check resetToken used status
            if (!passwordResetToken.isUsed()) {
                String email = passwordResetToken.getUser().getEmail();
                Optional<User> optionalUser = userRepository.findByEmail(email);
                // check user existence
                if (optionalUser.isPresent()) {
                    User user = optionalUser.get();
                    user.setPassword(newPassword);
                    // update token used status
                    passwordResetToken.setUsed(true);
                    // delete token after successfully update
                    passwordResetTokenRepository.delete(passwordResetToken);
                    return userRepository.save(user);
                } else {
                    throw new RuntimeException("User not found");
                }
            } else {
                throw new RuntimeException("Token has been used");
            }
        } else {
            throw new RuntimeException("Invalid token");
        }
    }
}
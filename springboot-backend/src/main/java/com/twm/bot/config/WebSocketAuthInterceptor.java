package com.twm.bot.config;

import com.twm.bot.middleware.JwtTokenUtil;
import com.twm.bot.model.user.User;
import com.twm.bot.repository.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
            logger.info("Authorization header: {}", authorizationHeader);

            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);
                logger.info("Extracted token: {}", token);

                try {
                    String username = jwtTokenUtil.getUsernameFromToken(token);
                    logger.info("Extracted username from token: {}", username);

                    if (username != null) {
                        User user = userRepository.findByEmail(username).orElse(null);
                        if (user != null) {
                            logger.info("User found: {}", user.getEmail());

                            // 創建並設置身份驗證物件
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    user, null, user.getAuthorities()
                            );

                            // 將身份驗證信息存入 SecurityContext
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            accessor.setUser(authentication); // 設置用戶到 WebSocket 會話中
                        } else {
                            logger.warn("User not found for username: {}", username);
                            closeWebSocketSession(accessor);
                            throw new SecurityException("User not found");
                        }
                    } else {
                        logger.warn("Could not extract username from token");
                        closeWebSocketSession(accessor);
                        throw new SecurityException("Invalid token: username extraction failed");
                    }
                } catch (Exception e) {
                    logger.error("Error validating token", e);
                    closeWebSocketSession(accessor);
                    throw new SecurityException("Token validation failed", e);
                }
            } else {
                logger.warn("No valid authorization header found");
                closeWebSocketSession(accessor);
                throw new SecurityException("Authorization header is missing or invalid");

            }
        }
        return message;
    }

    private void closeWebSocketSession(StompHeaderAccessor accessor) {
        WebSocketSession session = (WebSocketSession) accessor.getSessionAttributes().get("session");
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (IOException e) {
                logger.error("Error closing WebSocket session", e);
            }
        }
    }
}

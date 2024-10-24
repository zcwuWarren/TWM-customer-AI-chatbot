package com.twm.bot.middleware;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twm.bot.model.user.User;
import com.twm.bot.service.RedisService;
import jakarta.annotation.Nullable;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

import static org.apache.logging.log4j.util.Strings.isEmpty;

@Log4j2
@Component
public class JwtTokenFilter extends OncePerRequestFilter {
    private final JwtTokenUtil jwtTokenUtil;
    private final ObjectMapper objectMapper;

    public JwtTokenFilter(JwtTokenUtil jwtTokenUtil, ObjectMapper objectMapper, RedisService redisService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwtToken = retrieveToken(request);
            if (jwtToken == null || !jwtTokenUtil.validateToken(jwtToken)) {
                filterChain.doFilter(request, response);
            } else {
                User user = jwtTokenUtil.getUserFromToken(jwtToken);
                UsernamePasswordAuthenticationToken authAfterSuccessLogin = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                authAfterSuccessLogin.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authAfterSuccessLogin);

                // Logging the authenticated user details
                logger.info("Authenticated User: " + user.getUsername());
                logger.info("User Roles: " + user.getAuthorities());

                filterChain.doFilter(request, response);
            }
        }  catch (Exception e) {
            logger.error("JWT Authentication error: ", e);
            Map<String, String> errorMsg = Map.of("error", e.getMessage());
            handleException(response, HttpStatus.UNAUTHORIZED, errorMsg);
            SecurityContextHolder.clearContext();
        }
    }

    @Nullable
    public String retrieveToken(HttpServletRequest request) {
        final String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (isEmpty(header) || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.split(" ")[1].trim();
    }

    private void handleException(HttpServletResponse response, HttpStatus status, Map<String, String> message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), message);
    }
}

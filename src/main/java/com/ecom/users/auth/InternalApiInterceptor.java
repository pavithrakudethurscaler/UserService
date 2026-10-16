package com.ecom.users.auth;

import com.ecom.users.exceptions.InvalidTokenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protects /internal/** endpoints, which are called by other services (Notification Service), not by customers.
 * Callers send the shared secret in the X-Internal-Token header.
 */
@Component
public class InternalApiInterceptor implements HandlerInterceptor {
    private final byte[] internalApiToken;

    public InternalApiInterceptor(@Value("${internal.api-token:}") String internalApiToken) {
        this.internalApiToken = internalApiToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws InvalidTokenException {
        if (internalApiToken.length == 0) {
            // Fail closed: without a configured secret nobody can call internal endpoints
            throw new InvalidTokenException("Internal API token is not configured");
        }
        String provided = request.getHeader("X-Internal-Token");
        if (provided == null
                || !MessageDigest.isEqual(internalApiToken, provided.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidTokenException("Invalid internal API token");
        }
        return true;
    }
}

package com.ecom.users.services;

import com.ecom.users.clients.NotificationPublisher;
import com.ecom.users.exceptions.InvalidCredentialsException;
import com.ecom.users.exceptions.InvalidTokenException;
import com.ecom.users.exceptions.UserAlreadyExistsException;
import com.ecom.users.models.PasswordResetToken;
import com.ecom.users.models.Token;
import com.ecom.users.models.User;
import com.ecom.users.repos.PasswordResetTokenRepo;
import com.ecom.users.repos.TokenRepo;
import com.ecom.users.repos.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepo userRepo;
    private final TokenRepo tokenRepo;
    private final PasswordResetTokenRepo passwordResetTokenRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final NotificationPublisher notificationPublisher;
    private final long tokenValidityMs;
    private final long resetTokenValidityMs;
    private final String resetLinkBaseUrl;

    public UserService(UserRepo userRepo, TokenRepo tokenRepo, PasswordResetTokenRepo passwordResetTokenRepo,
                       BCryptPasswordEncoder passwordEncoder, NotificationPublisher notificationPublisher,
                       @Value("${auth.token.validity-ms:3600000}") long tokenValidityMs,
                       @Value("${auth.reset-token.validity-ms:900000}") long resetTokenValidityMs,
                       @Value("${auth.reset-link.base-url:http://localhost:8082/reset-password}") String resetLinkBaseUrl) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.passwordResetTokenRepo = passwordResetTokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.notificationPublisher = notificationPublisher;
        this.tokenValidityMs = tokenValidityMs;
        this.resetTokenValidityMs = resetTokenValidityMs;
        this.resetLinkBaseUrl = resetLinkBaseUrl;
    }

    public User signUp(String name, String email, String password) throws UserAlreadyExistsException {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email and password are required");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepo.findByEmail(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException("User with email " + normalizedEmail + " already exists");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(normalizedEmail);
        user.setHashedPassword(passwordEncoder.encode(password));
        user = userRepo.save(user);

        notificationPublisher.publish("user-" + user.getId() + "-registered", "USER_REGISTERED", Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "name", user.getName() == null ? "" : user.getName()));
        return user;
    }

    public Optional<User> findUser(Long id) {
        return userRepo.findById(id);
    }

    public Token login(String email, String password) throws InvalidCredentialsException {
        if (email == null || password == null) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        Optional<User> optionalUser = userRepo.findByEmail(email.trim().toLowerCase());
        // Same message for unknown email and wrong password, so callers cannot probe for registered emails
        if (optionalUser.isEmpty() || !passwordEncoder.matches(password, optionalUser.get().getHashedPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return tokenRepo.save(generateToken(optionalUser.get()));
    }

    /*
     A token is valid if
     1. Token exists in DB
     2. Token has not expired
     3. Token has not been marked as deleted (logged out)
     */
    public User validateToken(String tokenValue) throws InvalidTokenException {
        return tokenRepo.findByValueAndDeletedAndExpiryAtGreaterThan(tokenValue, false, System.currentTimeMillis())
                .map(Token::getUser)
                .orElseThrow(() -> new InvalidTokenException("Token is invalid or expired"));
    }

    public void logout(String tokenValue) throws InvalidTokenException {
        Token token = tokenRepo.findByValueAndDeleted(tokenValue, false)
                .orElseThrow(() -> new InvalidTokenException("Token is invalid or already logged out"));
        token.setDeleted(true);
        tokenRepo.save(token);
    }

    public User updateProfile(User user, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        user.setName(name.trim());
        return userRepo.save(user);
    }

    /*
     Creates a one-time reset token and sends the reset link through the Notification Service.
     Does nothing for unknown emails, so callers cannot probe for registered emails.
     The link is never written to this service's log.
     */
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        Optional<User> optionalUser = userRepo.findByEmail(email.trim().toLowerCase());
        if (optionalUser.isEmpty()) {
            return;
        }

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setValue(randomTokenValue());
        resetToken.setExpiryAt(System.currentTimeMillis() + resetTokenValidityMs);
        resetToken.setUser(optionalUser.get());
        resetToken = passwordResetTokenRepo.save(resetToken);

        User user = optionalUser.get();
        notificationPublisher.publish("password-reset-" + resetToken.getId(), "PASSWORD_RESET_REQUESTED", Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "name", user.getName() == null ? "" : user.getName(),
                "resetLink", resetLinkBaseUrl + "?token=" + resetToken.getValue(),
                "expiresAt", resetToken.getExpiryAt()));
        log.info("Password reset requested for user {}", user.getId());
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) throws InvalidTokenException {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required");
        }
        PasswordResetToken resetToken = passwordResetTokenRepo
                .findByValueAndDeletedAndExpiryAtGreaterThan(tokenValue, false, System.currentTimeMillis())
                .orElseThrow(() -> new InvalidTokenException("Reset token is invalid or expired"));

        User user = resetToken.getUser();
        user.setHashedPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        resetToken.setDeleted(true);
        passwordResetTokenRepo.save(resetToken);

        // Log the user out everywhere, since the old password may have been compromised
        List<Token> activeTokens = tokenRepo.findByUserAndDeleted(user, false);
        activeTokens.forEach(token -> token.setDeleted(true));
        tokenRepo.saveAll(activeTokens);
    }

    private Token generateToken(User user) {
        Token token = new Token();
        token.setValue(randomTokenValue());
        token.setExpiryAt(System.currentTimeMillis() + tokenValidityMs);
        token.setUser(user);
        return token;
    }

    private String randomTokenValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

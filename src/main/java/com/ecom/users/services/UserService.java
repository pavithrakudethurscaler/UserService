package com.ecom.users.services;

import com.ecom.users.exceptions.InvalidCredentialsException;
import com.ecom.users.exceptions.InvalidTokenException;
import com.ecom.users.exceptions.UserAlreadyExistsException;
import com.ecom.users.models.Token;
import com.ecom.users.models.User;
import com.ecom.users.repos.TokenRepo;
import com.ecom.users.repos.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class UserService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepo userRepo;
    private final TokenRepo tokenRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private final long tokenValidityMs;

    public UserService(UserRepo userRepo, TokenRepo tokenRepo, BCryptPasswordEncoder passwordEncoder,
                       @Value("${auth.token.validity-ms:3600000}") long tokenValidityMs) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.tokenValidityMs = tokenValidityMs;
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
        return userRepo.save(user);
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

    private Token generateToken(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        Token token = new Token();
        token.setValue(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        token.setExpiryAt(System.currentTimeMillis() + tokenValidityMs);
        token.setUser(user);
        return token;
    }
}

package com.ecom.users.controllers;

import com.ecom.users.dtos.ForgotPasswordRequestDto;
import com.ecom.users.dtos.LoginRequestDto;
import com.ecom.users.dtos.LoginResponseDto;
import com.ecom.users.dtos.LogoutRequestDto;
import com.ecom.users.dtos.ResetPasswordRequestDto;
import com.ecom.users.dtos.SignUpRequestDto;
import com.ecom.users.dtos.UpdateProfileRequestDto;
import com.ecom.users.dtos.UserResponseDto;
import com.ecom.users.exceptions.InvalidCredentialsException;
import com.ecom.users.exceptions.InvalidTokenException;
import com.ecom.users.exceptions.UserAlreadyExistsException;
import com.ecom.users.models.Token;
import com.ecom.users.models.User;
import com.ecom.users.services.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponseDto> signUp(@RequestBody SignUpRequestDto signUpRequestDto)
            throws UserAlreadyExistsException {
        User user = userService.signUp(signUpRequestDto.getName(),
                signUpRequestDto.getEmail(),
                signUpRequestDto.getPassword());
        return new ResponseEntity<>(UserResponseDto.from(user), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody LoginRequestDto loginRequestDto)
            throws InvalidCredentialsException {
        Token token = userService.login(loginRequestDto.getEmail(), loginRequestDto.getPassword());
        return new ResponseEntity<>(LoginResponseDto.from(token), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody LogoutRequestDto logoutRequestDto) throws InvalidTokenException {
        userService.logout(logoutRequestDto.getToken());
        return new ResponseEntity<>("Logged out", HttpStatus.OK);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getProfile(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization)
            throws InvalidTokenException {
        User user = userService.validateToken(extractBearerToken(authorization));
        return new ResponseEntity<>(UserResponseDto.from(user), HttpStatus.OK);
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateProfile(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                         @RequestBody UpdateProfileRequestDto updateProfileRequestDto)
            throws InvalidTokenException {
        User user = userService.validateToken(extractBearerToken(authorization));
        User updatedUser = userService.updateProfile(user, updateProfileRequestDto.getName());
        return new ResponseEntity<>(UserResponseDto.from(updatedUser), HttpStatus.OK);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto) {
        userService.requestPasswordReset(forgotPasswordRequestDto.getEmail());
        // Same response whether or not the email is registered
        return new ResponseEntity<>("If the email is registered, a password reset link has been sent",
                HttpStatus.ACCEPTED);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequestDto resetPasswordRequestDto)
            throws InvalidTokenException {
        userService.resetPassword(resetPasswordRequestDto.getToken(), resetPasswordRequestDto.getNewPassword());
        return new ResponseEntity<>("Password has been reset. Please log in again", HttpStatus.OK);
    }

    @GetMapping("/validate/{token}")
    public ResponseEntity<UserResponseDto> validateToken(@PathVariable String token) throws InvalidTokenException {
        User user = userService.validateToken(token);
        return new ResponseEntity<>(UserResponseDto.from(user), HttpStatus.OK);
    }

    private String extractBearerToken(String authorization) throws InvalidTokenException {
        String prefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(prefix) || authorization.length() == prefix.length()) {
            throw new InvalidTokenException("Missing Authorization: Bearer <token> header");
        }
        return authorization.substring(prefix.length()).trim();
    }
}

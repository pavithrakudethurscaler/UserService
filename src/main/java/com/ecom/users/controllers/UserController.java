package com.ecom.users.controllers;

import com.ecom.users.dtos.LoginRequestDto;
import com.ecom.users.dtos.LoginResponseDto;
import com.ecom.users.dtos.LogoutRequestDto;
import com.ecom.users.dtos.SignUpRequestDto;
import com.ecom.users.dtos.UserResponseDto;
import com.ecom.users.exceptions.InvalidCredentialsException;
import com.ecom.users.exceptions.InvalidTokenException;
import com.ecom.users.exceptions.UserAlreadyExistsException;
import com.ecom.users.models.Token;
import com.ecom.users.models.User;
import com.ecom.users.services.UserService;
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

    @GetMapping("/validate/{token}")
    public ResponseEntity<UserResponseDto> validateToken(@PathVariable String token) throws InvalidTokenException {
        User user = userService.validateToken(token);
        return new ResponseEntity<>(UserResponseDto.from(user), HttpStatus.OK);
    }
}

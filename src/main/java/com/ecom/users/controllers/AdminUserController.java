package com.ecom.users.controllers;

import com.ecom.users.dtos.UserResponseDto;
import com.ecom.users.exceptions.ForbiddenException;
import com.ecom.users.exceptions.InvalidTokenException;
import com.ecom.users.exceptions.UserNotFoundException;
import com.ecom.users.models.User;
import com.ecom.users.services.RoleService;
import com.ecom.users.services.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Administrator-only user management. The caller must have the ADMIN role. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    UserService userService;
    RoleService roleService;

    AdminUserController(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDto>> listUsers(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization)
            throws InvalidTokenException, ForbiddenException {
        List<UserResponseDto> users = roleService.listUsers(caller(authorization)).stream()
                .map(UserResponseDto::from)
                .toList();
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @PutMapping("/{id}/roles/admin")
    public ResponseEntity<UserResponseDto> grantAdmin(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization)
            throws InvalidTokenException, ForbiddenException, UserNotFoundException {
        return new ResponseEntity<>(UserResponseDto.from(roleService.grantAdmin(caller(authorization), id)), HttpStatus.OK);
    }

    @DeleteMapping("/{id}/roles/admin")
    public ResponseEntity<UserResponseDto> revokeAdmin(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization)
            throws InvalidTokenException, ForbiddenException, UserNotFoundException {
        return new ResponseEntity<>(UserResponseDto.from(roleService.revokeAdmin(caller(authorization), id)), HttpStatus.OK);
    }

    private User caller(String authorization) throws InvalidTokenException {
        String prefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(prefix) || authorization.length() == prefix.length()) {
            throw new InvalidTokenException("Missing Authorization: Bearer <token> header");
        }
        return userService.validateToken(authorization.substring(prefix.length()).trim());
    }
}

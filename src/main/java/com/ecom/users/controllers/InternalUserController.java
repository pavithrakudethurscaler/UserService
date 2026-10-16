package com.ecom.users.controllers;

import com.ecom.users.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Service-to-service endpoints, protected by the X-Internal-Token header (see InternalApiInterceptor). */
@RestController
@RequestMapping("/internal/api/v1/users")
public class InternalUserController {
    UserService userService;

    InternalUserController(UserService userService) {
        this.userService = userService;
    }

    /** Contact details for notifications. Only id, name and email are exposed. */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserContact(@PathVariable Long id) {
        return userService.findUser(id)
                .map(user -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", user.getId(),
                        "name", user.getName() == null ? "" : user.getName(),
                        "email", user.getEmail())))
                .orElse(ResponseEntity.notFound().build());
    }
}

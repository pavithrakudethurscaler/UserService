package com.ecom.users.services;

import com.ecom.users.exceptions.ForbiddenException;
import com.ecom.users.exceptions.UserNotFoundException;
import com.ecom.users.models.Role;
import com.ecom.users.models.RoleNames;
import com.ecom.users.models.User;
import com.ecom.users.repos.RoleRepo;
import com.ecom.users.repos.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {
    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepo roleRepo;
    private final UserRepo userRepo;
    /** Emails that are always given ADMIN (bootstrap for the first administrator) */
    private final Set<String> adminEmails;

    public RoleService(RoleRepo roleRepo, UserRepo userRepo, @Value("${auth.admin-emails:}") String adminEmails) {
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
        this.adminEmails = Arrays.stream(adminEmails.split(","))
                .map(e -> e.trim().toLowerCase())
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    /*
     On startup:
     1. Create the CUSTOMER and ADMIN roles if they do not exist
     2. Give CUSTOMER to users created before roles existed
     3. Give ADMIN to existing users listed in auth.admin-emails
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialiseRoles() {
        getOrCreate(RoleNames.CUSTOMER);
        getOrCreate(RoleNames.ADMIN);
        for (User user : userRepo.findAll()) {
            boolean changed = addRole(user, RoleNames.CUSTOMER);
            if (adminEmails.contains(user.getEmail())) {
                changed |= addRole(user, RoleNames.ADMIN);
            }
            if (changed) {
                userRepo.save(user);
            }
        }
        if (adminEmails.isEmpty()) {
            log.warn("auth.admin-emails (ADMIN_EMAILS) is empty: nobody can become an administrator until it is set");
        }
    }

    /** Roles for a newly registered user: CUSTOMER, plus ADMIN if their email is a bootstrap admin */
    public void assignSignupRoles(User user) {
        addRole(user, RoleNames.CUSTOMER);
        if (adminEmails.contains(user.getEmail())) {
            addRole(user, RoleNames.ADMIN);
            log.info("User {} registered with a bootstrap admin email and was given ADMIN", user.getEmail());
        }
    }

    public static boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> RoleNames.ADMIN.equals(r.getValue()));
    }

    public void requireAdmin(User caller) throws ForbiddenException {
        if (!isAdmin(caller)) {
            throw new ForbiddenException("Administrator role required");
        }
    }

    public List<User> listUsers(User caller) throws ForbiddenException {
        requireAdmin(caller);
        return userRepo.findAll();
    }

    @Transactional
    public User grantAdmin(User caller, Long userId) throws ForbiddenException, UserNotFoundException {
        requireAdmin(caller);
        User target = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        if (addRole(target, RoleNames.ADMIN)) {
            target = userRepo.save(target);
            log.info("User {} granted ADMIN to user {}", caller.getId(), target.getId());
        }
        return target;
    }

    @Transactional
    public User revokeAdmin(User caller, Long userId) throws ForbiddenException, UserNotFoundException {
        requireAdmin(caller);
        if (caller.getId().equals(userId)) {
            // Prevents the last administrator from locking everyone out by accident
            throw new ForbiddenException("Administrators cannot remove their own ADMIN role");
        }
        User target = userRepo.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        if (target.getRoles().removeIf(r -> RoleNames.ADMIN.equals(r.getValue()))) {
            target = userRepo.save(target);
            log.info("User {} revoked ADMIN from user {}", caller.getId(), target.getId());
        }
        return target;
    }

    private boolean addRole(User user, String roleName) {
        if (user.getRoles().stream().anyMatch(r -> roleName.equals(r.getValue()))) {
            return false;
        }
        user.getRoles().add(getOrCreate(roleName));
        return true;
    }

    private Role getOrCreate(String roleName) {
        return roleRepo.findByValue(roleName).orElseGet(() -> {
            Role role = new Role();
            role.setValue(roleName);
            return roleRepo.save(role);
        });
    }
}

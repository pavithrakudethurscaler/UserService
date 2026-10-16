package com.ecom.users.services;

import com.ecom.users.exceptions.ForbiddenException;
import com.ecom.users.exceptions.UserNotFoundException;
import com.ecom.users.models.User;
import com.ecom.users.repos.RoleRepo;
import com.ecom.users.repos.UserRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class RoleServiceTest {
    @Autowired UserRepo userRepo;
    @Autowired RoleRepo roleRepo;

    private User newUser(RoleService roles, String email) {
        User user = new User();
        user.setEmail(email);
        user.setHashedPassword("x");
        roles.assignSignupRoles(user);
        return userRepo.save(user);
    }

    @Test
    void startupCreatesRolesBackfillsCustomerAndBootstrapsAdminsIgnoringCase() {
        User legacy = new User();
        legacy.setEmail("bob@x.com");
        legacy.setHashedPassword("x");
        userRepo.save(legacy);

        new RoleService(roleRepo, userRepo, " BOB@x.com , other@x.com").initialiseRoles();

        assertThat(roleRepo.findByValue("CUSTOMER")).isPresent();
        assertThat(roleRepo.findByValue("ADMIN")).isPresent();
        assertThat(userRepo.findById(legacy.getId()).orElseThrow().getRoles())
                .extracting("value").containsExactlyInAnyOrder("CUSTOMER", "ADMIN");
    }

    @Test
    void adminCanGrantAndRevokeButNotRevokeSelf() throws Exception {
        RoleService roles = new RoleService(roleRepo, userRepo, "admin@x.com");
        roles.initialiseRoles();
        User admin = newUser(roles, "admin@x.com");
        User alice = newUser(roles, "alice@x.com");
        assertThat(RoleService.isAdmin(alice)).isFalse();

        assertThat(RoleService.isAdmin(roles.grantAdmin(admin, alice.getId()))).isTrue();
        assertThat(RoleService.isAdmin(roles.revokeAdmin(admin, alice.getId()))).isFalse();
        assertThatThrownBy(() -> roles.revokeAdmin(admin, admin.getId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> roles.grantAdmin(admin, 999L)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void nonAdminIsForbidden() {
        RoleService roles = new RoleService(roleRepo, userRepo, "");
        roles.initialiseRoles();
        User alice = newUser(roles, "alice@x.com");
        User bob = newUser(roles, "bob@x.com");

        assertThatThrownBy(() -> roles.listUsers(alice)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> roles.grantAdmin(alice, bob.getId())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> roles.revokeAdmin(alice, bob.getId())).isInstanceOf(ForbiddenException.class);
    }
}

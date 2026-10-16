package com.ecom.users.services;

import com.ecom.users.clients.NotificationPublisher;
import com.ecom.users.exceptions.InvalidCredentialsException;
import com.ecom.users.exceptions.InvalidTokenException;
import com.ecom.users.exceptions.UserAlreadyExistsException;
import com.ecom.users.models.PasswordResetToken;
import com.ecom.users.models.Token;
import com.ecom.users.models.User;
import com.ecom.users.repos.PasswordResetTokenRepo;
import com.ecom.users.repos.RoleRepo;
import com.ecom.users.repos.TokenRepo;
import com.ecom.users.repos.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** UserService against real repositories (H2), so token expiry/deletion queries are exercised for real. */
@DataJpaTest
class UserServiceTest {
    @Autowired UserRepo userRepo;
    @Autowired TokenRepo tokenRepo;
    @Autowired PasswordResetTokenRepo resetRepo;
    @Autowired RoleRepo roleRepo;

    NotificationPublisher publisher;
    UserService userService;

    @BeforeEach
    void setUp() {
        publisher = mock(NotificationPublisher.class);
        RoleService roleService = new RoleService(roleRepo, userRepo, "admin@x.com");
        roleService.initialiseRoles();
        userService = new UserService(userRepo, tokenRepo, resetRepo, new BCryptPasswordEncoder(), publisher,
                roleService, 3_600_000, 900_000, "http://app/reset");
    }

    @Test
    void signUpNormalisesEmailAssignsCustomerRoleAndPublishesEvent() throws Exception {
        User user = userService.signUp("Alice", "  Alice@X.com ", "pw");

        assertThat(user.getEmail()).isEqualTo("alice@x.com");
        assertThat(user.getHashedPassword()).isNotEqualTo("pw");
        assertThat(user.getRoles()).extracting("value").containsExactly("CUSTOMER");
        verify(publisher).publish(eq("user-" + user.getId() + "-registered"), eq("USER_REGISTERED"), any());
    }

    @Test
    void signUpGivesAdminToBootstrapEmail() throws Exception {
        User admin = userService.signUp("Admin", "ADMIN@x.com", "pw");
        assertThat(admin.getRoles()).extracting("value").containsExactlyInAnyOrder("CUSTOMER", "ADMIN");
    }

    @Test
    void signUpRejectsDuplicateEmailIgnoringCase() throws Exception {
        userService.signUp("A", "a@x.com", "pw");
        assertThatThrownBy(() -> userService.signUp("B", "A@X.COM", "pw2"))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void signUpRequiresEmailAndPassword() {
        assertThatThrownBy(() -> userService.signUp("A", " ", "pw")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userService.signUp("A", "a@x.com", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginGivesSameErrorForUnknownEmailAndWrongPassword() throws Exception {
        userService.signUp("A", "a@x.com", "pw");
        Throwable unknown = catchThrowable(() -> userService.login("nobody@x.com", "pw"));
        Throwable wrong = catchThrowable(() -> userService.login("a@x.com", "bad"));

        assertThat(unknown).isInstanceOf(InvalidCredentialsException.class);
        assertThat(wrong).isInstanceOf(InvalidCredentialsException.class).hasMessage(unknown.getMessage());
    }

    @Test
    void loginIssuesRandomTokenThatValidates() throws Exception {
        User user = userService.signUp("A", "a@x.com", "pw");
        Token t1 = userService.login("A@x.com", "pw");
        Token t2 = userService.login("a@x.com", "pw");

        assertThat(t1.getValue()).hasSizeGreaterThanOrEqualTo(40).isNotEqualTo(t2.getValue());
        assertThat(userService.validateToken(t1.getValue()).getId()).isEqualTo(user.getId());
    }

    @Test
    void validateRejectsExpiredAndLoggedOutTokens() throws Exception {
        userService.signUp("A", "a@x.com", "pw");
        Token token = userService.login("a@x.com", "pw");

        Token expired = userService.login("a@x.com", "pw");
        expired.setExpiryAt(System.currentTimeMillis() - 1);
        tokenRepo.save(expired);
        assertThatThrownBy(() -> userService.validateToken(expired.getValue())).isInstanceOf(InvalidTokenException.class);

        userService.logout(token.getValue());
        assertThatThrownBy(() -> userService.validateToken(token.getValue())).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> userService.logout(token.getValue())).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> userService.validateToken("nope")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void passwordResetForUnknownEmailDoesNothing() {
        userService.requestPasswordReset("nobody@x.com");
        assertThat(resetRepo.count()).isZero();
        verify(publisher, never()).publish(anyString(), eq("PASSWORD_RESET_REQUESTED"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void passwordResetFlowIsSingleUseAndRevokesAllSessions() throws Exception {
        userService.signUp("A", "a@x.com", "old");
        Token session = userService.login("a@x.com", "old");

        userService.requestPasswordReset("A@x.com");
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(anyString(), eq("PASSWORD_RESET_REQUESTED"), data.capture());
        PasswordResetToken reset = resetRepo.findAll().get(0);
        assertThat(data.getValue().get("resetLink")).isEqualTo("http://app/reset?token=" + reset.getValue());

        assertThatThrownBy(() -> userService.resetPassword(reset.getValue(), " "))
                .isInstanceOf(IllegalArgumentException.class);
        userService.resetPassword(reset.getValue(), "new");

        assertThatThrownBy(() -> userService.validateToken(session.getValue())).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> userService.login("a@x.com", "old")).isInstanceOf(InvalidCredentialsException.class);
        assertThat(userService.login("a@x.com", "new")).isNotNull();
        assertThatThrownBy(() -> userService.resetPassword(reset.getValue(), "again"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredResetTokenIsRejected() throws Exception {
        userService.signUp("A", "a@x.com", "old");
        userService.requestPasswordReset("a@x.com");
        PasswordResetToken reset = resetRepo.findAll().get(0);
        reset.setExpiryAt(System.currentTimeMillis() - 1);
        resetRepo.save(reset);

        assertThatThrownBy(() -> userService.resetPassword(reset.getValue(), "new"))
                .isInstanceOf(InvalidTokenException.class);
    }
}

package com.merchanthub;

import com.merchanthub.dto.AuthDtos.AuthResponse;
import com.merchanthub.service.AuthService;
import com.merchanthub.web.error.ApiExceptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired AuthService authService;

    @Test
    void registerThenLoginSucceedsWithTheRightPassword() {
        String email = "auth-" + UUID.randomUUID() + "@x.com";

        AuthResponse registered = authService.register("Test Merchant", email, "correct-horse-battery");
        assertThat(registered.token()).isNotBlank();
        assertThat(registered.merchant().email()).isEqualTo(email);

        AuthResponse loggedIn = authService.login(email, "correct-horse-battery");
        assertThat(loggedIn.merchant().id()).isEqualTo(registered.merchant().id());
    }

    @Test
    void loginFailsWithTheWrongPassword() {
        String email = "auth-" + UUID.randomUUID() + "@x.com";
        authService.register("Test Merchant", email, "correct-horse-battery");

        assertThatThrownBy(() -> authService.login(email, "wrong-password"))
                .isInstanceOf(ApiExceptions.Unauthorized.class);
    }

    @Test
    void loginFailsForAnUnknownEmail() {
        assertThatThrownBy(() -> authService.login("nobody-" + UUID.randomUUID() + "@x.com", "whatever"))
                .isInstanceOf(ApiExceptions.Unauthorized.class);
    }

    @Test
    void registeringTheSameEmailTwiceConflicts() {
        String email = "auth-" + UUID.randomUUID() + "@x.com";
        authService.register("First", email, "correct-horse-battery");

        assertThatThrownBy(() -> authService.register("Second", email, "another-password"))
                .isInstanceOf(ApiExceptions.Conflict.class);
    }
}

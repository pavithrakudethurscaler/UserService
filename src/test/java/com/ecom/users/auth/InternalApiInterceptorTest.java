package com.ecom.users.auth;

import com.ecom.users.exceptions.InvalidTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalApiInterceptorTest {
    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/api/v1/users/1");
        if (token != null) {
            request.addHeader("X-Internal-Token", token);
        }
        return request;
    }

    @Test
    void correctTokenPasses() throws Exception {
        assertThat(new InternalApiInterceptor("secret")
                .preHandle(request("secret"), new MockHttpServletResponse(), null)).isTrue();
    }

    @Test
    void missingOrWrongTokenIsRejected() {
        InternalApiInterceptor interceptor = new InternalApiInterceptor("secret");
        assertThatThrownBy(() -> interceptor.preHandle(request(null), new MockHttpServletResponse(), null))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> interceptor.preHandle(request("wrong"), new MockHttpServletResponse(), null))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void unconfiguredTokenRejectsEverything() {
        InternalApiInterceptor interceptor = new InternalApiInterceptor("");
        assertThatThrownBy(() -> interceptor.preHandle(request(""), new MockHttpServletResponse(), null))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("not configured");
    }
}

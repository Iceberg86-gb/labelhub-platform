package com.labelhub.api.security;

import com.labelhub.api.config.SecurityProperties;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenFilterTest {

    private static final String TOKEN = "configured-internal-token";

    private InternalTokenFilter filter(String configuredToken) {
        SecurityProperties properties = new SecurityProperties();
        properties.setInternalToken(configuredToken);
        return new InternalTokenFilter(properties);
    }

    private MockHttpServletRequest internalRequest(String headerToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/api");
        request.setRequestURI("/api/internal/ai-review/1/context");
        if (headerToken != null) {
            request.addHeader("X-Internal-Token", headerToken);
        }
        return request;
    }

    @Test
    void non_internal_path_passes_through_without_token() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/api");
        request.setRequestURI("/api/tasks/marketplace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(TOKEN).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void internal_path_with_matching_token_passes() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(TOKEN).doFilter(internalRequest(TOKEN), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void internal_path_with_wrong_token_is_unauthorized() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(TOKEN).doFilter(internalRequest("wrong-token"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void internal_path_with_missing_header_is_unauthorized() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(TOKEN).doFilter(internalRequest(null), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void blank_configured_token_rejects_even_a_blank_header() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter("  ").doFilter(internalRequest("  "), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}

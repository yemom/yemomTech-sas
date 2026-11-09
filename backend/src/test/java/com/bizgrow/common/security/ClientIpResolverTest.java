package com.YemomBizGrow.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClientIpResolver();
    }

    @Test
    @DisplayName("Uses X-Forwarded-For when remote address is trusted proxy (e.g. 127.0.0.1)")
    void usesForwardedForFromTrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "196.188.12.34, 10.0.0.1");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("196.188.12.34");
    }

    @Test
    @DisplayName("Uses X-Forwarded-For when remote address is private RFC 1918 address (e.g. 10.0.1.5)")
    void usesForwardedForFromPrivateProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.1.5");
        request.addHeader("X-Forwarded-For", "213.55.85.12");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("213.55.85.12");
    }

    @Test
    @DisplayName("Ignores spoofed X-Forwarded-For when remote address is untrusted public IP")
    void ignoresForwardedForFromUntrustedDirectClient() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");
        request.addHeader("X-Forwarded-For", "8.8.8.8");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("Defaults to remoteAddr when X-Forwarded-For is absent")
    void defaultsToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.22");

        String ip = resolver.resolveClientIp(request);
        assertThat(ip).isEqualTo("198.51.100.22");
    }
}

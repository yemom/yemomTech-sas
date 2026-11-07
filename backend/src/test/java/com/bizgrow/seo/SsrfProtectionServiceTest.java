package com.YemomBizGrow.seo;

import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.seo.crawler.SsrfProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsrfProtectionServiceTest {

    private SsrfProtectionService ssrfService;

    @BeforeEach
    void setUp() {
        ssrfService = new SsrfProtectionService();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",
            "127.0.1.1",
            "10.0.0.1",
            "10.255.255.255",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.0.1",
            "192.168.1.100",
            "169.254.169.254",
            "169.254.1.1",
            "0.0.0.0",
            "100.64.0.1"
    })
    @DisplayName("Blocks prohibited IPv4 addresses (loopback, private, metadata)")
    void blocksProhibitedIpv4(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(ssrfService.isProhibited(address))
                .as("IP %s should be prohibited", ip)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::1",
            "fc00::1",
            "fd00::1",
            "fe80::1"
    })
    @DisplayName("Blocks prohibited IPv6 addresses (loopback, unique local, link-local)")
    void blocksProhibitedIpv6(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(ssrfService.isProhibited(address))
                .as("IPv6 %s should be prohibited", ip)
                .isTrue();
    }

    @Test
    @DisplayName("Allows standard public IPs")
    void allowsPublicIps() throws UnknownHostException {
        InetAddress googleDns = InetAddress.getByName("8.8.8.8");
        assertThat(ssrfService.isProhibited(googleDns)).isFalse();

        InetAddress cloudflareDns = InetAddress.getByName("1.1.1.1");
        assertThat(ssrfService.isProhibited(cloudflareDns)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/file",
            "file:///etc/passwd",
            "gopher://example.com",
            "ldap://example.com",
            "data:text/plain;base64,SGVsbG8="
    })
    @DisplayName("Rejects non-HTTP and non-HTTPS URI schemes")
    void rejectsDisallowedSchemes(String url) {
        URI uri = URI.create(url);
        assertThatThrownBy(() -> ssrfService.validateAndResolve(uri))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only HTTP and HTTPS");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/admin",
            "https://localhost:8080",
            "http://metadata.google.internal/computeMetadata/v1",
            "http://169.254.169.254/latest/meta-data"
    })
    @DisplayName("Rejects known blocked hosts and cloud metadata endpoints")
    void rejectsBlockedHosts(String url) {
        URI uri = URI.create(url);
        assertThatThrownBy(() -> ssrfService.validateAndResolve(uri))
                .isInstanceOf(BadRequestException.class);
    }
}

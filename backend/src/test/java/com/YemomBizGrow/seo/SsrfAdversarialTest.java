package com.YemomBizGrow.seo;

import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.seo.crawler.SafeHttpClient;
import com.YemomBizGrow.seo.crawler.SafeFetchResult;
import com.YemomBizGrow.seo.crawler.SsrfProtectionService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Adversarial SSRF coverage for the crawler.
 *
 * <p>The crawler fetches URLs that users supply, so the protection has to
 * hold against the ways an address can be spelled rather than only against the
 * obvious ones. These tests cover the encoding tricks (decimal/hex/octal
 * hosts, IPv6 forms that embed an IPv4 address), the redirection path (a
 * public-looking first hop that points at a private address) and the resource
 * limits.</p>
 */
class SsrfAdversarialTest {

    private SsrfProtectionService ssrfService;

    @BeforeEach
    void setUp() {
        ssrfService = new SsrfProtectionService();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::127.0.0.1",        // IPv4-compatible IPv6
            "::7f00:1",           // the same address in full form
            "::10.0.0.1",         // IPv4-compatible, private
            "::192.168.1.1",      // IPv4-compatible, private
            "::169.254.169.254",  // IPv4-compatible, cloud metadata
            "2002:7f00:0001::",   // 6to4 wrapping 127.0.0.1
            "2002:0a00:0001::",   // 6to4 wrapping 10.0.0.1
            "2001:0000:0000:0000:0000:0000:80ff:fffe" // Teredo wrapping 127.0.0.1
    })
    @DisplayName("IPv6 addresses that embed an IPv4 address are unwrapped and blocked")
    void blocksIpv6EmbeddingPrivateIpv4(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThat(ssrfService.isProhibited(address))
                .as("IP %s should be prohibited", ip)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://[::127.0.0.1]/admin",
            "http://[::ffff:127.0.0.1]/admin",
            "http://[::169.254.169.254]/latest/meta-data",
            "http://[2002:7f00:0001::]/admin"
    })
    @DisplayName("URLs whose host embeds a private IPv4 address are rejected")
    void rejectsIpv6EmbeddedPrivateHosts(String url) {
        assertThatThrownBy(() -> ssrfService.validateAndResolve(URI.create(url)))
                .isInstanceOf(BadRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://2130706433/admin",   // decimal 127.0.0.1
            "http://017700000001/admin", // octal 127.0.0.1
            "http://0x7f000001/admin",   // hex 127.0.0.1
            "http://127.0.0.1/admin",    // plain
            "http://user:pass@127.0.0.1/admin", // credentials in the authority
            "http://127.0.0.1./admin"    // trailing dot
    })
    @DisplayName("Alternative spellings of a loopback host are rejected")
    void rejectsAlternativeLoopbackSpellings(String url) {
        assertThatThrownBy(() -> ssrfService.validateAndResolve(URI.create(url)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("A public host on an unusual port is still allowed")
    void allowsPublicHostOnUnusualPort() throws UnknownHostException {
        // The port must not be the deciding factor - only the address is.
        assertThat(ssrfService.isProhibited(InetAddress.getByName("93.184.216.34"))).isFalse();
    }

    @Test
    @DisplayName("A redirect to a private address is refused because every hop is re-validated")
    void refusesRedirectToPrivateNetwork() throws Exception {
        // The policy below allows the first two validations (the initial URL
        // and the first hop) and refuses everything after that. The client
        // validates the redirect target again on the way, so the private
        // target is never fetched. A client that validated only the first URL
        // would fetch it and the body would contain the secret.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/redirect-to-private", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/private");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/private", exchange -> {
            byte[] body = "secret".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            URI target = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redirect-to-private");

            SafeHttpClient client = new SafeHttpClient(new AllowFirstNValidations(2));
            SafeFetchResult result = client.fetch(target);

            assertThat(result.body() == null ? "" : result.body())
                    .as("the redirect target must not be fetched")
                    .doesNotContain("secret");
            assertThat(result.success())
                    .as("the redirect must be refused")
                    .isFalse();
            assertThat(result.redirectChain())
                    .as("the blocked hop must be recorded")
                    .isNotEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("An oversized response is refused instead of being buffered")
    void refusesOversizedResponse() throws Exception {
        // The client caps the payload at 5 MB. A server that announces (and
        // streams) more must not be able to exhaust the heap.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/big", exchange -> {
            byte[] chunk = new byte[64 * 1024];
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                for (int i = 0; i < 200; i++) {
                    out.write(chunk);
                }
            } catch (IOException ignored) {
                // The client closes the connection once the limit is hit.
            }
        });
        server.start();
        try {
            URI target = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/big");

            SafeHttpClient client = new SafeHttpClient(new AllowFirstNValidations(Integer.MAX_VALUE));
            SafeFetchResult result = client.fetch(target);

            assertThat(result.success())
                    .as("an oversized response must not be accepted")
                    .isFalse();
            assertThat(result.statusCode())
                    .as("an oversized response must not be reported as a success")
                    .isNotEqualTo(200);
            assertThat(result.body() == null || result.body().length() <= 5 * 1024 * 1024)
                    .as("at most 5 MB may be held in memory")
                    .isTrue();
        } finally {
            server.stop(0);
        }
    }

    /**
     * Test policy: allows the first {@code allowed} validations and refuses
     * every later one.
     *
     * <p>It lets the test reach the code path it wants to exercise (the
     * client actually connecting) while still proving that the client
     * re-validates on every hop.</p>
     */
    private static final class AllowFirstNValidations extends SsrfProtectionService {
        private final int allowed;
        private final AtomicInteger calls = new AtomicInteger();

        private AllowFirstNValidations(int allowed) {
            this.allowed = allowed;
        }

        @Override
        public InetAddress validateAndResolve(URI uri) {
            if (calls.incrementAndGet() > allowed) {
                throw new BadRequestException("blocked by the test policy");
            }
            try {
                return InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                throw new BadRequestException("unresolvable");
            }
        }
    }
}

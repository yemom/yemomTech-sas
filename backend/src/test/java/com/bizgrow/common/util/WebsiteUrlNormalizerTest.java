package com.bizgrow.common.util;

import com.bizgrow.common.exception.BadRequestException;
import com.bizgrow.common.util.WebsiteUrlNormalizer.NormalizedUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The URL gate is the only place that decides what a "website" is. Because a
 * future crawler will start from this same code, these tests pin both the
 * canonicalization rules and the anti-SSRF rejections.
 */
class WebsiteUrlNormalizerTest {

    @Test
    @DisplayName("lowercases host, strips default ports and empty paths")
    void normalizesCanonicalForm() {
        NormalizedUrl result = WebsiteUrlNormalizer.normalize("HTTPS://WWW.Example.COM:443/");

        assertThat(result.url()).isEqualTo("https://www.example.com");
        assertThat(result.domain()).isEqualTo("www.example.com");
    }

    @Test
    @DisplayName("http default port 80 is stripped, non-default ports are kept")
    void keepsNonDefaultPort() {
        assertThat(WebsiteUrlNormalizer.normalize("http://example.com:80/path").url())
                .isEqualTo("http://example.com/path");
        assertThat(WebsiteUrlNormalizer.normalize("http://example.com:8080/path").url())
                .isEqualTo("http://example.com:8080/path");
    }

    @Test
    @DisplayName("path and query survive normalization")
    void keepsPathAndQuery() {
        NormalizedUrl result = WebsiteUrlNormalizer.normalize("https://Example.com/Shop?cat=shoes");

        assertThat(result.url()).isEqualTo("https://example.com/Shop?cat=shoes");
        assertThat(result.domain()).isEqualTo("example.com");
    }

    @Test
    @DisplayName("an Ethiopian ccTLD domain is accepted")
    void acceptsEthiopianDomain() {
        assertThat(WebsiteUrlNormalizer.normalize("https://example.et").domain())
                .isEqualTo("example.et");
    }

    @ParameterizedTest
    @DisplayName("unsafe or non-web URLs are rejected")
    @ValueSource(strings = {
            "ftp://example.com",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "http://localhost/admin",
            "http://app.localhost",
            "https://intranet.local",
            "http://db.internal",
            "http://127.0.0.1/admin",
            "http://192.168.1.10",
            "http://10.0.0.1",
            "https://[::1]/",
            "http://user:pass@example.com",
            "http://example.com /login",
            "not a url",
            "",
            "   "
    })
    void rejectsInvalidUrls(String raw) {
        assertThatThrownBy(() -> WebsiteUrlNormalizer.normalize(raw))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("null input is rejected with a user-safe message")
    void rejectsNull() {
        assertThatThrownBy(() -> WebsiteUrlNormalizer.normalize(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("a bare hostname without a dot is rejected")
    void rejectsSingleLabelHost() {
        assertThatThrownBy(() -> WebsiteUrlNormalizer.normalize("http://example"))
                .isInstanceOf(BadRequestException.class);
    }
}

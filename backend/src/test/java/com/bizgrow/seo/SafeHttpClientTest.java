package com.YemomBizGrow.seo;

import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.seo.crawler.SafeFetchResult;
import com.YemomBizGrow.seo.crawler.SafeHttpClient;
import com.YemomBizGrow.seo.crawler.SsrfProtectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafeHttpClientTest {

    @Mock
    private SsrfProtectionService ssrfProtection;

    @Test
    @DisplayName("fetch blocks prohibited destination when SSRF validation rejects")
    void testFetchBlocksProhibitedDestination() {
        when(ssrfProtection.validateAndResolve(any()))
                .thenThrow(new BadRequestException("Target destination resolves to a private or restricted network address."));

        SafeHttpClient client = new SafeHttpClient(ssrfProtection);
        SafeFetchResult result = client.fetch(URI.create("http://127.0.0.1:8080/internal"));

        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(0);
    }

    @Test
    @DisplayName("fetch catches connect error gracefully when host unreachable")
    void testFetchHandlesUnreachableHostGracefully() throws Exception {
        // Point to a non-listening test port on a documentation IP (TEST-NET-1: 192.0.2.1)
        InetAddress dummyPublicIp = InetAddress.getByName("192.0.2.1");
        when(ssrfProtection.validateAndResolve(any())).thenReturn(dummyPublicIp);

        SafeHttpClient client = new SafeHttpClient(ssrfProtection);
        SafeFetchResult result = client.fetch(URI.create("http://example.com:65534/test"));

        assertThat(result.success()).isFalse();
        assertThat(result.statusCode()).isEqualTo(0);
    }
}

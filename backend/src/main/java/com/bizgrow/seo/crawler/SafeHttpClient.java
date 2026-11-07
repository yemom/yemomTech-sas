package com.YemomBizGrow.seo.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * SSRF-safe HTTP client for website analysis and crawling.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Pre-connection and pre-redirect IP validation via {@link SsrfProtectionService}</li>
 *   <li>Connection timeout (5s) and request timeout (10s)</li>
 *   <li>Max response size limit (5 MB)</li>
 *   <li>Strict redirect limit (max 5 hops) with DNS re-validation on every hop</li>
 * </ul>
 * </p>
 */
@Component
public class SafeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SafeHttpClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024; // 5 MB max response size
    private static final String USER_AGENT = "BizGrow-SEO-Crawler/1.0 (+https://bizgrow.et/bot)";

    private final SsrfProtectionService ssrfProtection;
    private final HttpClient httpClient;

    public SafeHttpClient(SsrfProtectionService ssrfProtection) {
        this.ssrfProtection = ssrfProtection;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public SafeFetchResult fetch(URI targetUri) {
        URI currentUri = targetUri;
        List<URI> redirectChain = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            try {
                ssrfProtection.validateAndResolve(currentUri);
            } catch (Exception e) {
                log.warn("SSRF blocked or DNS failed for {}: {}", currentUri, e.getMessage());
                long elapsed = System.currentTimeMillis() - startTime;
                return new SafeFetchResult(targetUri, currentUri, 0, null, null, elapsed, redirectChain, false);
            }

            HttpRequest request = HttpRequest.newBuilder(currentUri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.1")
                    .GET()
                    .build();

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (Exception e) {
                log.warn("Failed fetching {}: {}", currentUri, e.getMessage());
                long elapsed = System.currentTimeMillis() - startTime;
                return new SafeFetchResult(targetUri, currentUri, 0, null, null, elapsed, redirectChain, false);
            }

            int status = response.statusCode();
            String contentType = response.headers().firstValue("Content-Type").orElse("");

            if (status >= 300 && status < 400 && response.headers().firstValue("Location").isPresent()) {
                String location = response.headers().firstValue("Location").get();
                URI nextUri = currentUri.resolve(location);
                redirectChain.add(nextUri);
                currentUri = nextUri;
                continue;
            }

            // Read bounded response body
            String body = null;
            try (InputStream in = response.body();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int totalRead = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalRead += read;
                    if (totalRead > MAX_BODY_BYTES) {
                        break;
                    }
                }
                body = out.toString(StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Error reading response body from {}: {}", currentUri, e.getMessage());
            }

            long elapsed = System.currentTimeMillis() - startTime;
            return new SafeFetchResult(targetUri, currentUri, status, contentType, body, elapsed, redirectChain, true);
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new SafeFetchResult(targetUri, currentUri, 310, "text/plain", "Too many redirects", elapsed, redirectChain, false);
    }
}

package com.YemomBizGrow.seo.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SSRF-safe HTTP client with IP destination pinning.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Pre-connection DNS resolution and comprehensive IP validation via {@link SsrfProtectionService}</li>
 *   <li>Connection-time IP pinning: dials TCP socket directly to the validated IP address, preventing DNS rebinding and TOCTOU attacks</li>
 *   <li>Full TLS certificate chain validation and RFC 2818 hostname verification</li>
 *   <li>Strict SNI and HTTP Host header preservation</li>
 *   <li>Bounded redirect following (max 5 hops) with DNS re-validation on every redirect hop</li>
 *   <li>Strict timeouts (5s connect, 10s read) and maximum payload limit (5 MB)</li>
 * </ul>
 * </p>
 */
@Component
public class SafeHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SafeHttpClient.class);

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 5 * 1024 * 1024; // 5 MB max response size
    private static final String USER_AGENT = "YemomBizGrow-SEO-Crawler/1.0 (+https://yemombizgrow.com/bot)";

    private final SsrfProtectionService ssrfProtection;

    public SafeHttpClient(SsrfProtectionService ssrfProtection) {
        this.ssrfProtection = ssrfProtection;
    }

    public SafeFetchResult fetch(URI targetUri) {
        URI currentUri = targetUri;
        List<URI> redirectChain = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            InetAddress pinnedIp;
            try {
                pinnedIp = ssrfProtection.validateAndResolve(currentUri);
            } catch (Exception e) {
                log.warn("SSRF blocked or DNS failed for {}: {}", currentUri, e.getMessage());
                long elapsed = System.currentTimeMillis() - startTime;
                return new SafeFetchResult(targetUri, currentUri, 0, null, null, elapsed, redirectChain, false);
            }

            int port = currentUri.getPort() != -1 ? currentUri.getPort()
                    : ("https".equalsIgnoreCase(currentUri.getScheme()) ? 443 : 80);
            boolean isHttps = "https".equalsIgnoreCase(currentUri.getScheme());
            String host = currentUri.getHost();

            try (Socket rawSocket = new Socket()) {
                // Pin connection strictly to pre-validated IP to eliminate DNS rebinding / TOCTOU
                rawSocket.connect(new InetSocketAddress(pinnedIp, port), CONNECT_TIMEOUT_MS);
                rawSocket.setSoTimeout(READ_TIMEOUT_MS);

                Socket activeSocket = rawSocket;
                if (isHttps) {
                    SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(rawSocket, host, port, true);
                    SSLParameters params = sslSocket.getSSLParameters();
                    params.setServerNames(List.of(new SNIHostName(host)));
                    sslSocket.setSSLParameters(params);
                    sslSocket.startHandshake();

                    HostnameVerifier verifier = HttpsURLConnection.getDefaultHostnameVerifier();
                    if (!verifier.verify(host, sslSocket.getSession())) {
                        throw new javax.net.ssl.SSLPeerUnverifiedException("Certificate hostname verification failed for " + host);
                    }
                    activeSocket = sslSocket;
                }

                // Send HTTP/1.1 request
                String path = currentUri.getRawPath();
                if (path == null || path.isBlank()) {
                    path = "/";
                }
                if (currentUri.getRawQuery() != null) {
                    path += "?" + currentUri.getRawQuery();
                }

                String requestHeaders = "GET " + path + " HTTP/1.1\r\n" +
                        "Host: " + host + (currentUri.getPort() != -1 ? ":" + currentUri.getPort() : "") + "\r\n" +
                        "User-Agent: " + USER_AGENT + "\r\n" +
                        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.1\r\n" +
                        "Connection: close\r\n\r\n";

                OutputStream out = activeSocket.getOutputStream();
                out.write(requestHeaders.getBytes(StandardCharsets.US_ASCII));
                out.flush();

                // Read HTTP response
                InputStream in = new BufferedInputStream(activeSocket.getInputStream());
                String statusLine = readLine(in);
                if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return new SafeFetchResult(targetUri, currentUri, 0, null, null, elapsed, redirectChain, false);
                }

                int statusCode = parseStatusCode(statusLine);
                Map<String, String> headers = readHeaders(in);
                String contentType = headers.getOrDefault("content-type", "");
                String location = headers.get("location");

                if (statusCode >= 300 && statusCode < 400 && location != null) {
                    URI nextUri = currentUri.resolve(location);
                    redirectChain.add(nextUri);
                    currentUri = nextUri;
                    continue;
                }

                // Read body with bounded size check
                ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int totalRead = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    totalRead += read;
                    if (totalRead > MAX_BODY_BYTES) {
                        log.warn("Response body from {} exceeded limit of {} bytes", currentUri, MAX_BODY_BYTES);
                        long elapsed = System.currentTimeMillis() - startTime;
                        return new SafeFetchResult(targetUri, currentUri, 413, "text/plain", "Payload Too Large: exceeded 5MB", elapsed, redirectChain, false);
                    }
                    bodyOut.write(buffer, 0, read);
                }

                String body = bodyOut.toString(StandardCharsets.UTF_8);
                long elapsed = System.currentTimeMillis() - startTime;
                return new SafeFetchResult(targetUri, currentUri, statusCode, contentType, body, elapsed, redirectChain, true);

            } catch (Exception e) {
                log.warn("Connection or read failed for {} (pinned to {}): {}", currentUri, pinnedIp, e.getMessage());
                long elapsed = System.currentTimeMillis() - startTime;
                return new SafeFetchResult(targetUri, currentUri, 0, null, null, elapsed, redirectChain, false);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new SafeFetchResult(targetUri, currentUri, 310, "text/plain", "Too many redirects", elapsed, redirectChain, false);
    }

    private String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                if (next == '\n' || next == -1) break;
                lineBuffer.write(b);
                lineBuffer.write(next);
            } else if (b == '\n') {
                break;
            } else {
                lineBuffer.write(b);
            }
        }
        if (lineBuffer.size() == 0 && b == -1) return null;
        return lineBuffer.toString(StandardCharsets.US_ASCII);
    }

    private Map<String, String> readHeaders(InputStream in) throws Exception {
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isBlank()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String val = line.substring(colon + 1).trim();
                headers.put(key, val);
            }
        }
        return headers;
    }

    private int parseStatusCode(String statusLine) {
        String[] parts = statusLine.split("\\s+");
        if (parts.length >= 2) {
            try {
                return Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}

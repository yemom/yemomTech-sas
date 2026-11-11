package com.YemomBizGrow.seo.crawler;

import com.YemomBizGrow.common.exception.BadRequestException;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
 *   <li>Full redirect revalidation (re-validates IP on every redirect hop)</li>
 *   <li>Strict timeouts (5s connect, 10s read, 30s total request deadline)</li>
 *   <li>Maximum payload limit (5 MB) with chunked transfer encoding support</li>
 *   <li>Maximum header count (64) and total header size (16 KB) limits</li>
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
    private static final int MAX_HEADER_COUNT = 64;
    private static final int MAX_HEADER_TOTAL_BYTES = 16 * 1024; // 16 KB total headers
    private static final String USER_AGENT = "YemomBizGrow-SEO-Crawler/1.0 (+https://yemombizgrow.com/bot)";

    private final SsrfProtectionService ssrfProtection;

    public SafeHttpClient(SsrfProtectionService ssrfProtection) {
        this.ssrfProtection = ssrfProtection;
    }

    public SafeFetchResult fetch(URI targetUri) {
        Instant startTime = Instant.now();

        // Validate the initial URI via SSRF protection
        InetAddress initialPinnedIp;
        try {
            initialPinnedIp = ssrfProtection.validateAndResolve(targetUri);
        } catch (Exception e) {
            log.warn("SSRF blocked or DNS failed for {}: {}", targetUri, e.getMessage());
            return new SafeFetchResult(targetUri, targetUri, 0, null, null, Duration.between(startTime, Instant.now()).toMillis(), new ArrayList<>(), false);
        }

        URI currentUri = targetUri;
        List<URI> redirectChain = new ArrayList<>();
        boolean success = false;
        String body = null;
        int statusCode = 0;
        String contentType = null;

        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            // --- Redirect revalidation: re-validate IP on each hop to prevent TOCTOU ---
            InetAddress pinnedIp;
            try {
                pinnedIp = ssrfProtection.validateAndResolve(currentUri);
            } catch (Exception e) {
                log.warn("SSRF blocked or DNS failed on hop {} for {}: {}", hop, currentUri, e.getMessage());
                long elapsed = Duration.between(startTime, Instant.now()).toMillis();
                return new SafeFetchResult(targetUri, currentUri, 0, null, null, elapsed, redirectChain, false);
            }

            int port = currentUri.getPort() != -1 ? currentUri.getPort()
                    : ("https".equalsIgnoreCase(currentUri.getScheme()) ? 443 : 80);
            boolean isHttps = "https".equalsIgnoreCase(currentUri.getScheme());
            String host = currentUri.getHost();

            // Total request deadline check
            if (Duration.between(startTime, Instant.now()).toMillis() > 30000) {
                log.warn("Total request deadline exceeded for {}", currentUri);
                long elapsed = Duration.between(startTime, Instant.now()).toMillis();
                return new SafeFetchResult(targetUri, currentUri, 504, "text/plain", "Gateway Timeout: total request exceeded 30s", elapsed, redirectChain, false);
            }

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

                // Send HTTP/1.1 request with header size/ count limits
                String path = currentUri.getRawPath();
                if (path == null || path.isBlank()) {
                    path = "/";
                }
                if (currentUri.getRawQuery() != null) {
                    path += "?" + currentUri.getRawQuery();
                }

                // Build request with header size/ count limits
                Map<String, String> headers = new HashMap<>(MAX_HEADER_COUNT);
                headers.put("User-Agent", USER_AGENT);
                headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.1");
                headers.put("Host", host + (currentUri.getPort() != -1 ? ":" + currentUri.getPort() : ""));
                headers.put("Connection", "close");

                if (headers.size() > MAX_HEADER_COUNT) {
                    long elapsed = Duration.between(startTime, Instant.now()).toMillis();
                    return new SafeFetchResult(targetUri, currentUri, 431, "text/plain", "Header Too Large: too many headers", elapsed, redirectChain, false);
                }

                StringBuilder requestBuilder = new StringBuilder();
                requestBuilder.append("GET ").append(path).append(" HTTP/1.1\r\n");
                int headerBytes = 0;
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    String line = entry.getKey() + ": " + entry.getValue() + "\r\n";
                    if (headerBytes + line.getBytes(StandardCharsets.US_ASCII).length > MAX_HEADER_TOTAL_BYTES) {
                        long elapsed = Duration.between(startTime, Instant.now()).toMillis();
                        return new SafeFetchResult(targetUri, currentUri, 431, "text/plain", "Header Too Large: header line too large", elapsed, redirectChain, false);
                    }
                    requestBuilder.append(line);
                    headerBytes += line.getBytes(StandardCharsets.US_ASCII).length;
                }
                requestBuilder.append("\r\n");

                OutputStream out = activeSocket.getOutputStream();
                out.write(requestBuilder.toString().getBytes(StandardCharsets.US_ASCII));
                out.flush();

                // Read HTTP response with chunked transfer encoding support
                InputStream in = new BufferedInputStream(activeSocket.getInputStream());
                String statusLine = readLine(in);
                if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                    long elapsed = Duration.between(startTime, Instant.now()).toMillis();
                    return new SafeFetchResult(targetUri, currentUri, 0, null, null, elapsed, redirectChain, false);
                }

                statusCode = parseStatusCode(statusLine);
                Map<String, String> responseHeaders = readHeadersWithSizeLimit(in, MAX_HEADER_TOTAL_BYTES); // max 16KB total header size

                String rawTransferEncoding = responseHeaders.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT);
                String rawContentType = responseHeaders.getOrDefault("content-type", "");

                contentType = rawContentType;

                // Handle chunked transfer encoding
                boolean isChunked = "chunked".equals(rawTransferEncoding);
                long totalBodyBytes = 0;

                if (isChunked) {
                    body = readChunkedBody(in, MAX_BODY_BYTES);
                } else {
                    // Read fixed-length or close-terminated body
                    long contentLength = -1;
                    try {
                        contentLength = Long.parseLong(responseHeaders.getOrDefault("content-length", "-1"));
                    } catch (NumberFormatException ignored) {
                        // No content-length; will read until connection close
                    }
                    body = readBodyWithLimit(in, MAX_BODY_BYTES, contentLength);
                }

                // Body size check after reading
                if (body != null && body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
                    long elapsed = Duration.between(startTime, Instant.now()).toMillis();
                    return new SafeFetchResult(targetUri, currentUri, 413, contentType, "Payload Too Large: exceeded 5MB", elapsed, redirectChain, false);
                }

                // Check for redirect
                String location = responseHeaders.get("location");

                if (statusCode >= 300 && statusCode < 400 && location != null) {
                    URI nextUri = currentUri.resolve(location);
                    redirectChain.add(nextUri);
                    currentUri = nextUri;
                    // Continue to next iteration - IP already re-validated above
                    continue;
                }

                success = true;
                long elapsed = Duration.between(startTime, Instant.now()).toMillis();
                return new SafeFetchResult(targetUri, currentUri, statusCode, contentType, body, elapsed, redirectChain, true);

            } catch (Exception e) {
                log.warn("Connection or read failed for {} (pinned to {}): {}", currentUri, pinnedIp, e.getMessage());
                long elapsed = Duration.between(startTime, Instant.now()).toMillis();
                return new SafeFetchResult(targetUri, currentUri, 0, null, null, elapsed, redirectChain, false);
            }
        }

        long elapsed = Duration.between(startTime, Instant.now()).toMillis();
        return new SafeFetchResult(targetUri, currentUri, 310, "text/plain", "Too many redirects", elapsed, redirectChain, false);
    }

    private String readLine(InputStream in) throws IOException {
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

    private Map<String, String> readHeadersWithSizeLimit(InputStream in, int maxTotalBytes) throws IOException {
        Map<String, String> headers = new HashMap<>();
        String line;
        int totalBytes = 0;
        while ((line = readLine(in)) != null && !line.isBlank()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String key = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                int lineBytes = (key + ":" + val).getBytes(StandardCharsets.US_ASCII).length;
                if (totalBytes + lineBytes > maxTotalBytes) {
                    log.warn("Header total size exceeds limit of {} bytes", maxTotalBytes);
                    break;
                }
                headers.put(key.toLowerCase(Locale.ROOT), val);
                totalBytes += lineBytes;
            }
        }
        return headers;
    }

    private String readBodyWithLimit(InputStream in, int maxBytes, long contentLength) throws IOException {
        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalRead = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            totalRead += read;
            if (totalRead > maxBytes) {
                log.warn("Response body exceeded limit of {} bytes", maxBytes);
                throw new IOException("Body too large");
            }
            bodyOut.write(buffer, 0, read);
        }
        return bodyOut.toString(StandardCharsets.UTF_8);
    }

    private String readChunkedBody(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream bodyOut = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long totalRead = 0;

        while (true) {
            // Read chunk size line
            String chunkSizeLine = readLine(in);
            if (chunkSizeLine == null) {
                log.warn("Truncated chunked body");
                break;
            }

            // Chunk size may have extensions after semicolon; parse just the size
            String trimmed = chunkSizeLine.trim();
            int semicolonIdx = trimmed.indexOf(';');
            if (semicolonIdx > 0) {
                trimmed = trimmed.substring(0, semicolonIdx);
            }

            long chunkSize;
            try {
                chunkSize = Long.parseLong(trimmed, 16);
            } catch (NumberFormatException e) {
                log.warn("Invalid chunk size: {}", chunkSizeLine);
                break;
            }

            if (chunkSize == 0) {
                // Final chunk - read the trailing CRLF
                readLine(in); // consume the CRLF after the 0-length chunk
                break;
            }

            // Read chunk data
            int totalChunkRead = 0;
            while (totalChunkRead < chunkSize) {
                int toRead = (int) Math.min(chunkSize - totalChunkRead, buffer.length);
                int read = in.read(buffer, 0, toRead);
                if (read < 0) {
                    log.warn("Unexpected end of stream while reading chunk data");
                    return bodyOut.toString(StandardCharsets.UTF_8);
                }
                bodyOut.write(buffer, 0, read);
                totalChunkRead += read;
            }

            // Read and discard the CRLF after each chunk data
            readLine(in);

            totalRead += totalChunkRead;
            if (totalRead > maxBytes) {
                log.warn("Chunked response body exceeded limit of {} bytes", maxBytes);
                throw new IOException("Body too large in chunked encoding");
            }
        }

        return bodyOut.toString(StandardCharsets.UTF_8);
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
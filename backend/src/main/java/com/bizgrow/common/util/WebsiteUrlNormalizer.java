package com.bizgrow.common.util;

import com.bizgrow.common.exception.BadRequestException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates and normalizes website URLs without ever fetching them.
 *
 * <p>Why this lives in common/util: organization-profile URLs and registered
 * websites share identical rules, and a future SEO crawler must start from this
 * same gate (plus DNS/private-network checks) — keeping one normalization point
 * prevents divergent interpretations of "the same domain".</p>
 *
 * <p>Security posture for the first slice: the backend only stores the URL, so
 * there is no server-side request and therefore no SSRF surface. The validator
 * still rejects localhost, IP literals, intranet names, credentials-in-URL and
 * non-http(s) schemes so that stored data can never feed a vulnerable fetcher later.</p>
 */
public final class WebsiteUrlNormalizer {

    /** hostname: labels of 1-63 chars, at least two labels, no leading/trailing hyphen. */
    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /** Immutable result of a successful normalization. */
    public record NormalizedUrl(String url, String domain) {
    }

    private WebsiteUrlNormalizer() {
    }

    /**
     * @param raw user-supplied URL
     * @return canonical form: lowercased scheme+host, default port stripped, empty path omitted
     * @throws BadRequestException with a user-safe message when the URL is invalid or unsafe to store
     */
    public static NormalizedUrl normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("URL is required.");
        }
        String candidate = raw.trim();
        // Reject whitespace/control chars before parsing to keep error messages simple.
        if (candidate.chars().anyMatch(c -> c <= 0x20)) {
            throw new BadRequestException("URL must not contain spaces or control characters.");
        }

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            throw new BadRequestException("URL is not valid.");
        }

        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BadRequestException("Only http:// and https:// URLs are allowed.");
        }
        if (uri.getUserInfo() != null) {
            throw new BadRequestException("URL must not contain credentials.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("URL must include a valid domain.");
        }

        String domain = host.toLowerCase(Locale.ROOT);
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        if (!isValidPublicDomain(domain)) {
            throw new BadRequestException("URL must point to a public domain (localhost, IP addresses and "
                    + "internal hostnames are not allowed).");
        }

        int port = uri.getPort();
        boolean defaultPort = port == -1 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);

        StringBuilder url = new StringBuilder(scheme).append("://").append(domain);
        if (!defaultPort) {
            url.append(':').append(port);
        }
        if (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath())) {
            url.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            url.append('?').append(uri.getRawQuery());
        }
        return new NormalizedUrl(url.toString(), domain);
    }

    private static boolean isValidPublicDomain(String domain) {
        if (domain.length() > 253 || !domain.contains(".")) {
            return false;
        }
        if (IPV4.matcher(domain).matches() || domain.startsWith("[") /* IPv6 literal */) {
            return false;
        }
        if (domain.equals("localhost") || domain.endsWith(".localhost")
                || domain.endsWith(".local") || domain.endsWith(".internal")) {
            return false;
        }
        return DOMAIN_PATTERN.matcher(domain).matches();
    }
}

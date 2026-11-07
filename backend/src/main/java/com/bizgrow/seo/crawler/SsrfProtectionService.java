package com.YemomBizGrow.seo.crawler;

import com.YemomBizGrow.common.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Enterprise SSRF (Server-Side Request Forgery) protection engine.
 *
 * <p>Strictly blocks:
 * <ul>
 *   <li>Localhost and loopback addresses (127.0.0.0/8, ::1)</li>
 *   <li>Private RFC 1918 IPv4 ranges (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)</li>
 *   <li>Link-local and cloud metadata addresses (169.254.0.0/16, fe80::/10, metadata.google.internal)</li>
 *   <li>Unique local IPv6 ranges (fc00::/7)</li>
 *   <li>Broadcast, zero network (0.0.0.0/8), and documentation ranges</li>
 *   <li>Non-HTTP and non-HTTPS protocols (file://, gopher://, ftp://, ldap://, etc.)</li>
 * </ul>
 * </p>
 */
@Component
public class SsrfProtectionService {

    private static final Logger log = LoggerFactory.getLogger(SsrfProtectionService.class);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "metadata.google.internal",
            "instance-data",
            "169.254.169.254"
    );

    /**
     * Validates an absolute URI and verifies all resolved IP addresses.
     *
     * @param uri URI to validate
     * @return the first verified public InetAddress
     * @throws BadRequestException if the target address violates SSRF policy
     */
    public InetAddress validateAndResolve(URI uri) {
        if (uri == null) {
            throw new BadRequestException("Target URL cannot be null.");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Only HTTP and HTTPS protocols are allowed for website crawling.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BadRequestException("Invalid hostname in URL.");
        }

        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(normalizedHost) || normalizedHost.endsWith(".localhost") || normalizedHost.endsWith(".internal")) {
            log.warn("SSRF blocked prohibited hostname: {}", host);
            throw new BadRequestException("Target host is not permitted: " + host);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalizedHost);
        } catch (UnknownHostException e) {
            throw new BadRequestException("Host could not be resolved: " + host);
        }

        if (addresses == null || addresses.length == 0) {
            throw new BadRequestException("No network address found for host: " + host);
        }

        for (InetAddress address : addresses) {
            if (isProhibited(address)) {
                log.warn("SSRF blocked prohibited IP address {} for host {}", address.getHostAddress(), host);
                throw new BadRequestException("Target destination resolves to a private or restricted network address.");
            }
        }

        return addresses[0];
    }

    /**
     * Evaluates whether an IP address belongs to loopback, private, link-local, or cloud metadata ranges.
     */
    public boolean isProhibited(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
            return true;
        }

        if (address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }

        if (address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet4Address ipv4) {
            byte[] bytes = ipv4.getAddress();
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // 0.0.0.0/8 (current network)
            if (b0 == 0) return true;
            // 10.0.0.0/8 (private)
            if (b0 == 10) return true;
            // 127.0.0.0/8 (loopback)
            if (b0 == 127) return true;
            // 169.254.0.0/16 (link local & cloud metadata)
            if (b0 == 169 && b1 == 254) return true;
            // 172.16.0.0/12 (private)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;
            // 192.168.0.0/16 (private)
            if (b0 == 192 && b1 == 168) return true;
            // 100.64.0.0/10 (carrier-grade NAT)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return true;
            // 192.0.0.0/24, 192.0.2.0/24 (test/documentation)
            if (b0 == 192 && b1 == 0) return true;
            // 198.51.100.0/24, 203.0.113.0/24 (documentation)
            if (b0 == 198 && b1 == 51) return true;
            if (b0 == 203 && b1 == 0) return true;
            // 224.0.0.0/4 (multicast) & 240.0.0.0/4 (reserved)
            if (b0 >= 224) return true;
        }

        if (address instanceof Inet6Address ipv6) {
            byte[] bytes = ipv6.getAddress();
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // ::1 (loopback)
            if (address.isLoopbackAddress()) return true;
            // fc00::/7 (unique local address)
            if ((b0 & 0xFE) == 0xFC) return true;
            // fe80::/10 (link-local)
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) return true;
            // 2001:db8::/32 (documentation)
            if (b0 == 0x20 && b1 == 0x01 && (bytes[2] & 0xFF) == 0x0D && (bytes[3] & 0xFF) == 0xB8) return true;
        }

        return false;
    }
}

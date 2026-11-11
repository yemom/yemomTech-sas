package com.YemomBizGrow.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Resolves the real client IP address safely.
 *
 * <p>Prevents IP spoofing: only honors {@code X-Forwarded-For} if the immediate
 * peer connection originates from a trusted local or private reverse proxy
 * (e.g. AWS ALB, Nginx, or Kubernetes Ingress).</p>
 */
@Component
public class ClientIpResolver {

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank() && isTrustedProxy(remoteAddr)) {
            String firstIp = forwardedFor.split(",")[0].trim();
            if (!firstIp.isBlank()) {
                return firstIp;
            }
        }

        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip.trim());
            return addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}

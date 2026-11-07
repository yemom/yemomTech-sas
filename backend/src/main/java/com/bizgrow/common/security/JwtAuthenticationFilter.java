package com.YemomBizGrow.common.security;

import com.YemomBizGrow.user.User;
import com.YemomBizGrow.user.UserRepository;
import com.YemomBizGrow.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Validates the {@code Authorization: Bearer} header on every request.
 *
 * <p>Why the user row is re-read per request (a deliberate extra SELECT):
 * account suspension/deactivation and deletion take effect immediately instead of
 * lasting up to the full access-token TTL. With a 15-minute token and typical
 * SaaS traffic this is cheap, and it removes the need for a global token
 * deny-list. Organization permissions are checked separately per operation.</p>
 *
 * <p>Invalid tokens do not throw here — the request simply continues
 * unauthenticated, and the security entry point returns a clean 401. This keeps
 * expired-token handling identical for every endpoint.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty() && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    UUID userId = jwtService.parseUserId(token);
                    User user = userRepository.findById(userId).orElse(null);
                    if (user != null && user.getStatus() == UserStatus.ACTIVE) {
                        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getStatus());
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (RuntimeException ignored) {
                    // Invalid/expired token -> stay unauthenticated -> 401 from entry point.
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}

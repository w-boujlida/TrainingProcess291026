package com.adient.training.security;

import com.adient.training.entity.AppUser;
import com.adient.training.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Endpoints still reachable while a password change is pending. */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "/api/auth/me", "/api/auth/change-password");

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            var claims = jwtUtil.parse(header.substring(7));
            if (claims.isPresent()) {
                // Reload the user so deactivations / role changes take effect immediately.
                AppUser user = userRepository.findById(Long.valueOf(claims.get().getSubject())).orElse(null);
                if (user != null && user.isActive()) {
                    if (user.isMustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(req.getRequestURI())) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\",\"message\":\"Vous devez changer votre mot de passe.\"}");
                        return;
                    }
                    var principal = new AuthUser(user.getId(), user.getUsername(), user.getRole());
                    var auth = new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(req, res);
    }
}

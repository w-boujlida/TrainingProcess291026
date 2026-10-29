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
import org.springframework.web.servlet.LocaleResolver;
import com.adient.training.service.I18nService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.Map;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Endpoints still reachable while a password change is pending. */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "/api/auth/me", "/api/auth/change-password", "/api/auth/logout", "/api/i18n");

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final I18nService i18n;
    private final ObjectMapper mapper;
    private final LocaleResolver localeResolver;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository, I18nService i18n,
                         ObjectMapper mapper, LocaleResolver localeResolver) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.i18n = i18n;
        this.mapper = mapper;
        this.localeResolver = localeResolver;
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
                    req.setAttribute(AuditFilter.ACTOR, new AuthUser(user.getId(), user.getUsername(), user.getRole()));
                    req.setAttribute(AuditFilter.DEVICE, claims.get().get("deviceName", String.class));
                    if (user.isMustChangePassword() && !PASSWORD_CHANGE_ALLOWED.contains(req.getRequestURI())) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json;charset=UTF-8");
                        mapper.writeValue(res.getWriter(), Map.of("code", "PASSWORD_CHANGE_REQUIRED",
                                "message", i18n.translate("Vous devez changer votre mot de passe.", localeResolver.resolveLocale(req))));
                        return;
                    }
                    if (!user.getRole().hasModuleAccess() && !PASSWORD_CHANGE_ALLOWED.contains(req.getRequestURI())) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json;charset=UTF-8");
                        mapper.writeValue(res.getWriter(), Map.of("code", "MODULE_ACCESS_DENIED",
                                "message", i18n.translate("Accès refusé.", localeResolver.resolveLocale(req))));
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

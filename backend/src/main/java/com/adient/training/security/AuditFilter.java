package com.adient.training.security;

import com.adient.training.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.regex.Pattern;

public class AuditFilter extends OncePerRequestFilter {
    public static final String ACTOR = "audit.actor";
    public static final String USERNAME = "audit.username";
    public static final String DEVICE = "audit.device";
    public static final String AFFECTED = "audit.affected";
    public static final String REJECTED = "audit.rejected";
    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);
    private static final Pattern KNOWN_PATH = Pattern.compile(
            "/api/(?:auth/(?:login|logout|me|change-password)|audit-events"
            + "|users(?:/roles|/hr-import/(?:preview|apply)|/excel/(?:import|export|template)|/\\d+(?:/status|/reset-password|/hr-profile)?)?"
            + "|recruitment/(?:staff|positions|hierarchy|excel/(?:import|export|template)|candidates(?:/assignment|/\\d+(?:/status|/booklet(?:/candidate-signature|/trainer-signature)?)?)?)"
            + "|training-sheets/candidates/\\d+(?:/periods/\\d+/(?:trainer-signature|team-leader-signature)|/supervisor-signature|/quality-signature)?"
            + "|workstation-forms/candidates/\\d+(?:/(?:INITIAL|REFRESHER)/(?:steps/\\d+/signature|operator-signature))?)");
    private final AuditService audit;
    private final String trustedProxyHost;

    public AuditFilter(AuditService audit, String trustedProxyHost) {
        this.audit = audit;
        this.trustedProxyHost = trustedProxyHost;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || request.getMethod().equals("OPTIONS");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        try {
            chain.doFilter(request, wrapped);
            completed = true;
        } finally {
            String resource = KNOWN_PATH.matcher(request.getRequestURI()).matches() ? request.getRequestURI() : "/api/other";
            AuthUser actor = (AuthUser) request.getAttribute(ACTOR);
            String ip = request.getRemoteAddr();
            String source = "PEER";
            if (!trustedProxyHost.isBlank()) {
                try {
                    boolean trusted = Arrays.stream(InetAddress.getAllByName(trustedProxyHost))
                            .anyMatch(address -> address.getHostAddress().equals(request.getRemoteAddr()));
                    String forwarded = request.getHeader("X-Real-IP");
                    if (trusted && forwarded != null && forwarded.matches("[0-9a-fA-F:.]{2,64}")) {
                        ip = forwarded;
                        source = "PROXY";
                    }
                } catch (UnknownHostException ex) {
                    log.warn("Audit: proxy de confiance introuvable, utilisation de l'adresse du pair.");
                }
            }
            try {
                audit.record(new AuditService.Event(actor == null ? null : actor.id(),
                        actor == null ? (String) request.getAttribute(USERNAME) : actor.username(),
                        actor == null ? null : actor.role().name(), action(request.getMethod(), resource), resource,
                        request.getMethod(), completed ? wrapped.getStatus() : 500, ip, request.getRemoteAddr(), source,
                        request.getHeader("User-Agent"), (String) request.getAttribute(DEVICE),
                        (Integer) request.getAttribute(AFFECTED), (Integer) request.getAttribute(REJECTED)));
            } catch (DataAccessException | org.springframework.transaction.TransactionException ex) {
                log.error("AUDIT_WRITE_FAILED: evenement non persiste pour {} {}", request.getMethod(), resource, ex);
                wrapped.setHeader("X-Audit-Status", "failed");
            }
            wrapped.copyBodyToResponse();
        }
    }

    private static String action(String method, String resource) {
        if (resource.equals("/api/auth/login")) return "CONNEXION";
        if (resource.equals("/api/auth/logout")) return "DECONNEXION";
        if (resource.endsWith("/change-password")) return "CHANGEMENT_MOT_DE_PASSE";
        if (resource.endsWith("/reset-password")) return "REINITIALISATION_MOT_DE_PASSE";
        if (resource.endsWith("/excel/import")) return "IMPORT_EXCEL";
        if (resource.equals("/api/users/hr-import/preview")) return "CONSULTATION";
        if (resource.equals("/api/users/hr-import/apply")) return "IMPORT_EXCEL";
        if (resource.endsWith("/excel/export") || resource.endsWith("/excel/export-selection")) return "EXPORT_EXCEL";
        if (resource.equals("/api/users/selection")) return "MODIFICATION";
        if (resource.endsWith("/excel/template")) return "MODELE_EXCEL";
        if (resource.endsWith("-signature") || resource.endsWith("/signature")) return "SIGNATURE";
        return switch (method) {
            case "GET" -> "CONSULTATION";
            case "POST" -> "CREATION";
            case "PUT", "PATCH" -> "MODIFICATION";
            case "DELETE" -> "SUPPRESSION";
            default -> "REQUETE";
        };
    }
}

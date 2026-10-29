package com.adient.training.controller;

import com.adient.training.security.AuthUser;
import com.adient.training.security.AuditFilter;
import com.adient.training.service.UserHrImportService;
import com.adient.training.service.UserHrImportService.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/hr-import")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserHrImportController {
    private final UserHrImportService service;
    public UserHrImportController(UserHrImportService service) { this.service = service; }
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Preview> preview(@AuthenticationPrincipal AuthUser actor, @RequestParam MultipartFile file) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.preview(actor, file));
    }
    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Result> apply(@AuthenticationPrincipal AuthUser actor, @RequestParam MultipartFile file,
                                        @RequestParam String confirmation, HttpServletRequest request) {
        Result result = service.apply(actor, file, confirmation);
        request.setAttribute(AuditFilter.AFFECTED, result.updated() + result.created());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
    }
}

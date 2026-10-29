package com.adient.training.controller;

import com.adient.training.dto.Dtos.*;
import com.adient.training.security.AuthUser;
import com.adient.training.service.UserService;
import com.adient.training.service.UserExcelService;
import com.adient.training.dto.RecruitmentDtos.ImportResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import com.adient.training.security.AuditFilter;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserExcelService excelService;

    public UserController(UserService userService, UserExcelService excelService) {
        this.userService = userService;
        this.excelService = excelService;
    }

    @GetMapping("/excel/template")
    public ResponseEntity<byte[]> template(@AuthenticationPrincipal AuthUser actor) {
        return excel(excelService.template(actor), "modele_import_utilisateurs.xlsx");
    }

    @GetMapping("/excel/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser actor) {
        return excel(excelService.export(actor), "utilisateurs_" + LocalDate.now() + ".xlsx");
    }

    @PostMapping("/excel/export-selection")
    public ResponseEntity<byte[]> exportSelection(@AuthenticationPrincipal AuthUser actor,
                                                  @RequestBody List<Long> ids) {
        return excel(excelService.exportSelection(actor, ids), "utilisateurs_selection_" + LocalDate.now() + ".xlsx");
    }

    @PostMapping("/selection")
    public List<UserDto> applySelection(@AuthenticationPrincipal AuthUser actor,
                                       @Valid @RequestBody UserSelectionRequest body,
                                       HttpServletRequest request) {
        var result = userService.applySelection(actor, body);
        request.setAttribute(AuditFilter.AFFECTED, result.size());
        return result;
    }

    @PostMapping(value = "/excel/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importFile(@AuthenticationPrincipal AuthUser actor, @RequestParam("file") MultipartFile file,
                                   HttpServletRequest request) {
        ImportResult result = excelService.importFile(actor, file);
        request.setAttribute(AuditFilter.AFFECTED, result.created());
        request.setAttribute(AuditFilter.REJECTED, result.skipped());
        return result;
    }

    private static ResponseEntity<byte[]> excel(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.list();
    }

    @GetMapping("/roles")
    public List<RoleDto> assignableRoles(@AuthenticationPrincipal AuthUser actor) {
        return userService.assignableRoles(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@AuthenticationPrincipal AuthUser actor, @Valid @RequestBody CreateUserRequest req) {
        return userService.create(actor, req);
    }

    @PutMapping("/{id}")
    public UserDto update(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id,
                          @Valid @RequestBody UpdateUserRequest req) {
        return userService.update(actor, id, req);
    }

    @PatchMapping("/{id}/status")
    public UserDto setStatus(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id,
                             @Valid @RequestBody StatusRequest req) {
        return userService.setStatus(actor, id, req.active());
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id) {
        return userService.resetPassword(actor, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser actor, @PathVariable Long id) {
        userService.delete(actor, id);
    }
}

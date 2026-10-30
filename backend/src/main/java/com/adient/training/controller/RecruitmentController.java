package com.adient.training.controller;

import com.adient.training.dto.RecruitmentDtos.*;
import com.adient.training.entity.RecruitmentCategory;
import com.adient.training.security.AuthUser;
import com.adient.training.service.CandidateExcelService;
import com.adient.training.service.CandidateService;
import com.adient.training.service.IntegrationBookletService;
import com.adient.training.service.RecruitmentHierarchyService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import com.adient.training.security.AuditFilter;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruitment")
public class RecruitmentController {

    private final CandidateService service;
    private final CandidateExcelService excelService;
    private final IntegrationBookletService bookletService;
    private final RecruitmentHierarchyService hierarchyService;

    public RecruitmentController(CandidateService service, CandidateExcelService excelService,
                                 IntegrationBookletService bookletService, RecruitmentHierarchyService hierarchyService) {
        this.service = service;
        this.excelService = excelService;
        this.bookletService = bookletService;
        this.hierarchyService = hierarchyService;
    }

    @GetMapping("/candidates")
    public List<CandidateDto> list(@RequestParam(required = false) RecruitmentCategory category) {
        return service.list(category);
    }

    @GetMapping("/candidates/{id}")
    public CandidateDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/staff")
    public List<StaffDto> staff() {
        return service.staff();
    }

    @GetMapping("/positions")
    public Map<RecruitmentCategory, List<String>> positions() {
        Map<RecruitmentCategory, List<String>> map = new EnumMap<>(RecruitmentCategory.class);
        for (RecruitmentCategory c : RecruitmentCategory.values()) {
            if (c.hasFixedPositions()) map.put(c, c.getPositions());
        }
        return map;
    }

    @PostMapping("/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateDto create(@AuthenticationPrincipal AuthUser actor, @Valid @RequestBody CandidateRequest req) {
        return service.create(actor, req);
    }

    @PutMapping("/candidates/{id}")
    public CandidateDto update(@PathVariable Long id, @Valid @RequestBody CandidateRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/candidates/{id}/status")
    public CandidateDto setStatus(@PathVariable Long id, @Valid @RequestBody CandidateStatusRequest req) {
        return service.setStatus(id, req.status(), req.position());
    }

    @PatchMapping("/candidates/assignment")
    public List<CandidateDto> assign(@Valid @RequestBody AssignmentRequest req, HttpServletRequest request) {
        List<CandidateDto> updated = service.assign(req);
        request.setAttribute(AuditFilter.AFFECTED, updated.size());
        return updated;
    }

    @GetMapping("/hierarchy")
    public Hierarchy hierarchy(@RequestParam(required = false) RecruitmentCategory category) {
        return hierarchyService.hierarchy(category);
    }

    @GetMapping("/candidates/{id}/booklet")
    public IntegrationBookletDto getBooklet(@PathVariable Long id) {
        return bookletService.get(id);
    }

    @PutMapping("/candidates/{id}/booklet")
    public IntegrationBookletDto updateBooklet(@PathVariable Long id,
                                               @Valid @RequestBody IntegrationBookletUpdateRequest request) {
        return bookletService.update(id, request);
    }

    @PostMapping("/candidates/{id}/booklet/candidate-signature")
    public IntegrationBookletDto signCandidate(@PathVariable Long id,
                                               @AuthenticationPrincipal AuthUser actor,
                                               @Valid @RequestBody IntegrationBookletSignatureRequest request) {
        return bookletService.signCandidate(id, actor, request.signature());
    }

    @PostMapping("/candidates/{id}/booklet/trainer-signature")
    public IntegrationBookletDto signTrainer(@PathVariable Long id,
                                             @AuthenticationPrincipal AuthUser actor) {
        return bookletService.signTrainer(id, actor);
    }

    @DeleteMapping("/candidates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/excel/export")
    public ResponseEntity<byte[]> export(@RequestParam RecruitmentCategory category) {
        String name = "recrutement_" + category.name().toLowerCase() + "_" + LocalDate.now() + ".xlsx";
        return excel(excelService.export(category), name);
    }

    @GetMapping("/excel/template")
    public ResponseEntity<byte[]> template(@RequestParam RecruitmentCategory category) {
        return excel(excelService.template(category), "modele_import_" + category.name().toLowerCase() + ".xlsx");
    }

    @PostMapping(value = "/excel/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importFile(@AuthenticationPrincipal AuthUser actor,
                                   @RequestParam RecruitmentCategory category,
                                   @RequestParam("file") MultipartFile file, HttpServletRequest request) {
        ImportResult result = excelService.importFile(actor, category, file);
        request.setAttribute(AuditFilter.AFFECTED, result.created() + result.updated());
        request.setAttribute(AuditFilter.REJECTED, result.skipped());
        return result;
    }

    private static ResponseEntity<byte[]> excel(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}

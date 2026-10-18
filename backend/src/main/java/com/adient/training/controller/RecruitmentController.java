package com.adient.training.controller;

import com.adient.training.dto.RecruitmentDtos.*;
import com.adient.training.entity.RecruitmentCategory;
import com.adient.training.security.AuthUser;
import com.adient.training.service.CandidateExcelService;
import com.adient.training.service.CandidateService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/recruitment")
public class RecruitmentController {

    private final CandidateService service;
    private final CandidateExcelService excelService;

    public RecruitmentController(CandidateService service, CandidateExcelService excelService) {
        this.service = service;
        this.excelService = excelService;
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
        return service.setStatus(id, req.status());
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
                                   @RequestParam("file") MultipartFile file) {
        return excelService.importFile(actor, category, file);
    }

    private static ResponseEntity<byte[]> excel(byte[] body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}

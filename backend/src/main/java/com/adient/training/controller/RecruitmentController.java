package com.adient.training.controller;

import com.adient.training.dto.RecruitmentDtos.*;
import com.adient.training.entity.RecruitmentCategory;
import com.adient.training.security.AuthUser;
import com.adient.training.service.CandidateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recruitment")
public class RecruitmentController {

    private final CandidateService service;

    public RecruitmentController(CandidateService service) {
        this.service = service;
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
}

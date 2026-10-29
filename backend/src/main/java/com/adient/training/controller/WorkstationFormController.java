package com.adient.training.controller;

import com.adient.training.dto.WorkstationDtos.*;
import com.adient.training.entity.WorkstationCycle.Kind;
import com.adient.training.security.AuthUser;
import com.adient.training.service.WorkstationFormService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workstation-forms/candidates/{candidateId}")
public class WorkstationFormController {
    private final WorkstationFormService service;
    public WorkstationFormController(WorkstationFormService service) { this.service = service; }
    @GetMapping
    public FormDto get(@PathVariable Long candidateId) { return service.get(candidateId); }
    @PutMapping
    public FormDto update(@PathVariable Long candidateId, @AuthenticationPrincipal AuthUser actor,
                          @Valid @RequestBody FormUpdate request) {
        return service.update(candidateId, actor, request);
    }
    @PostMapping("/{kind}/steps/{stepId}/signature")
    public FormDto signStep(@PathVariable Long candidateId, @PathVariable Kind kind, @PathVariable Long stepId,
                            @AuthenticationPrincipal AuthUser actor, @Valid @RequestBody ApprovalRequest request) {
        return service.signStep(candidateId, kind, stepId, actor, request.version());
    }
    @PostMapping("/{kind}/operator-signature")
    public FormDto signOperator(@PathVariable Long candidateId, @PathVariable Kind kind,
                                @AuthenticationPrincipal AuthUser actor, @Valid @RequestBody SignatureRequest request) {
        return service.signOperator(candidateId, kind, actor, request);
    }
}

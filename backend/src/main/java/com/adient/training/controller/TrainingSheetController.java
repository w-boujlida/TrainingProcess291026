package com.adient.training.controller;

import com.adient.training.dto.TrainingSheetDtos.TrainingSheetDto;
import com.adient.training.dto.TrainingSheetDtos.TrainingSheetUpdateRequest;
import com.adient.training.security.AuthUser;
import com.adient.training.service.TrainingSheetService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/training-sheets/candidates/{candidateId}")
public class TrainingSheetController {

    private final TrainingSheetService service;

    public TrainingSheetController(TrainingSheetService service) {
        this.service = service;
    }

    @GetMapping
    public TrainingSheetDto get(@PathVariable Long candidateId) {
        return service.get(candidateId);
    }

    @PutMapping
    public TrainingSheetDto update(@PathVariable Long candidateId, @AuthenticationPrincipal AuthUser actor,
                                   @Valid @RequestBody TrainingSheetUpdateRequest request) {
        return service.update(candidateId, actor, request);
    }

    @PostMapping("/periods/{periodId}/trainer-signature")
    public TrainingSheetDto signTrainer(@PathVariable Long candidateId, @PathVariable Long periodId,
                                        @AuthenticationPrincipal AuthUser actor) {
        return service.signTrainer(candidateId, periodId, actor);
    }

    @PostMapping("/periods/{periodId}/team-leader-signature")
    public TrainingSheetDto signTeamLeader(@PathVariable Long candidateId, @PathVariable Long periodId,
                                           @AuthenticationPrincipal AuthUser actor) {
        return service.signTeamLeader(candidateId, periodId, actor);
    }

    @PostMapping("/supervisor-signature")
    public TrainingSheetDto signSupervisor(@PathVariable Long candidateId, @AuthenticationPrincipal AuthUser actor) {
        return service.signSupervisor(candidateId, actor);
    }

    @PostMapping("/quality-signature")
    public TrainingSheetDto signQuality(@PathVariable Long candidateId, @AuthenticationPrincipal AuthUser actor) {
        return service.signQuality(candidateId, actor);
    }
}

package com.adient.training.service;

import com.adient.training.dto.TrainingSheetDtos.*;
import com.adient.training.entity.*;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.TrainingSheetRepository;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class TrainingSheetService {

    private final TrainingSheetRepository sheets;
    private final UserRepository users;

    public TrainingSheetService(TrainingSheetRepository sheets, UserRepository users) {
        this.sheets = sheets;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public TrainingSheetDto get(Long candidateId) {
        return TrainingSheetDto.of(find(candidateId));
    }

    @Transactional
    public TrainingSheetDto update(Long candidateId, AuthUser actor, TrainingSheetUpdateRequest request) {
        TrainingSheet sheet = find(candidateId);
        requireEditor(sheet, actor);
        if (request.version() == null || sheet.getVersion() != request.version()) {
            throw new ApiException(HttpStatus.CONFLICT, "La fiche a été modifiée. Rechargez-la avant de continuer.");
        }
        validatePeriods(sheet.getPeriods(), request.periods());

        int firstChanged = Integer.MAX_VALUE;
        for (int i = 0; i < sheet.getPeriods().size(); i++) {
            TrainingPeriod period = sheet.getPeriods().get(i);
            PeriodUpdate update = request.periods().get(i);
            if (!sameContent(period, update)) firstChanged = Math.min(firstChanged, i);
            period.setStartDate(update.startDate());
            period.setEndDate(update.endDate());
            for (int j = 0; j < period.getCompetencies().size(); j++) {
                TrainingCompetency competency = period.getCompetencies().get(j);
                CompetencyUpdate competencyUpdate = update.competencies().get(j);
                competency.setRating(competencyUpdate.rating());
                competency.setComment(clean(competencyUpdate.comment()));
            }
        }

        TransferOpinions opinions = request.transferOpinions();
        boolean opinionsChanged = !Objects.equals(sheet.getBehaviorOpinion(), clean(opinions.behavior()))
                || !Objects.equals(sheet.getKnowledgeOpinion(), clean(opinions.knowledge()))
                || !Objects.equals(sheet.getMachineOpinion(), clean(opinions.machine()))
                || !Objects.equals(sheet.getSewingOpinion(), clean(opinions.sewing()));
        sheet.setBehaviorOpinion(clean(opinions.behavior()));
        sheet.setKnowledgeOpinion(clean(opinions.knowledge()));
        sheet.setMachineOpinion(clean(opinions.machine()));
        sheet.setSewingOpinion(clean(opinions.sewing()));

        // Later approvals depend on the preceding weeks; earlier weeks remain valid.
        for (int i = firstChanged; i < sheet.getPeriods().size(); i++) {
            TrainingPeriod period = sheet.getPeriods().get(i);
            period.setTrainerSignedBy(null);
            period.setTrainerSignedAt(null);
            period.setTeamLeaderSignedBy(null);
            period.setTeamLeaderSignedAt(null);
        }
        if (firstChanged != Integer.MAX_VALUE || opinionsChanged) {
            sheet.setSupervisorSignedBy(null);
            sheet.setSupervisorSignedAt(null);
            sheet.setQualitySignedBy(null);
            sheet.setQualitySignedAt(null);
            sheet.touch();
        }
        return TrainingSheetDto.of(sheets.saveAndFlush(sheet));
    }

    @Transactional
    public TrainingSheetDto signTrainer(Long candidateId, Long periodId, AuthUser actor) {
        requireRole(actor, Role.FORMATRICE);
        TrainingSheet sheet = find(candidateId);
        requireAssignedTrainer(sheet, actor);
        TrainingPeriod period = period(sheet, periodId);
        requireReadyForWeeklyApproval(sheet, period);
        period.setTrainerSignedBy(user(actor));
        period.setTrainerSignedAt(LocalDateTime.now());
        return persistApproval(sheet);
    }

    @Transactional
    public TrainingSheetDto signTeamLeader(Long candidateId, Long periodId, AuthUser actor) {
        requireRole(actor, Role.TEAM_LEADER);
        TrainingSheet sheet = find(candidateId);
        requireAssignedLeader(sheet, actor);
        TrainingPeriod period = period(sheet, periodId);
        requireReadyForWeeklyApproval(sheet, period);
        period.setTeamLeaderSignedBy(user(actor));
        period.setTeamLeaderSignedAt(LocalDateTime.now());
        return persistApproval(sheet);
    }

    @Transactional
    public TrainingSheetDto signSupervisor(Long candidateId, AuthUser actor) {
        requireRole(actor, Role.SUPERVISEUR);
        TrainingSheet sheet = find(candidateId);
        if (sheet.getPeriods().size() != 5 || sheet.getPeriods().stream().anyMatch(p -> !isApproved(p))) {
            throw badRequest("La formatrice ou le Team Leader doit valider chacune des cinq semaines avant le Superviseur.");
        }
        if (sheet.getSupervisorSignedAt() != null) throw badRequest("La fiche est déjà validée par le Superviseur.");
        sheet.setSupervisorSignedBy(user(actor));
        sheet.setSupervisorSignedAt(LocalDateTime.now());
        return persistApproval(sheet);
    }

    @Transactional
    public TrainingSheetDto signQuality(Long candidateId, AuthUser actor) {
        requireRole(actor, Role.QUALITY);
        TrainingSheet sheet = find(candidateId);
        if (sheet.getSupervisorSignedAt() == null) {
            throw badRequest("Le Superviseur doit valider la fiche avant Qualité.");
        }
        if (sheet.getQualitySignedAt() != null) throw badRequest("La fiche est déjà validée par Qualité.");
        sheet.setQualitySignedBy(user(actor));
        sheet.setQualitySignedAt(LocalDateTime.now());
        return persistApproval(sheet);
    }

    private TrainingSheetDto persistApproval(TrainingSheet sheet) {
        sheet.touch();
        return TrainingSheetDto.of(sheets.saveAndFlush(sheet));
    }

    private TrainingSheet find(Long candidateId) {
        return sheets.findByCandidateId(candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Fiche de formation introuvable."));
    }

    private TrainingPeriod period(TrainingSheet sheet, Long periodId) {
        return sheet.getPeriods().stream().filter(p -> p.getId().equals(periodId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Semaine de formation introuvable."));
    }

    private AppUser user(AuthUser actor) {
        return users.findById(actor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable."));
    }

    private static void validatePeriods(List<TrainingPeriod> existing, List<PeriodUpdate> requested) {
        if (requested == null || requested.size() != 5 || existing.size() != 5) {
            throw badRequest("La fiche doit contenir les cinq semaines du modèle SAGE.");
        }
        LocalDate previousEnd = null;
        for (int i = 0; i < existing.size(); i++) {
            TrainingPeriod saved = existing.get(i);
            PeriodUpdate update = requested.get(i);
            if (!saved.getId().equals(update.id()) || update.competencies().size() != saved.getCompetencies().size()) {
                throw badRequest("Les semaines et critères doivent correspondre au modèle de cette fiche.");
            }
            for (int j = 0; j < saved.getCompetencies().size(); j++) {
                CompetencyUpdate competency = update.competencies().get(j);
                if (!saved.getCompetencies().get(j).getId().equals(competency.id())) {
                    throw badRequest("Un critère n'appartient pas à la semaine indiquée.");
                }
                if (competency.rating() != null && (competency.rating() < 1 || competency.rating() > 5)) {
                    throw badRequest("Chaque note doit être comprise entre 1 et 5.");
                }
            }
            if ((update.startDate() == null) != (update.endDate() == null)) {
                throw badRequest("Renseignez la date de début et de fin de la semaine, ou laissez les deux vides.");
            }
            if (update.startDate() == null) continue;
            if (update.endDate().isBefore(update.startDate())) {
                throw badRequest("La date de fin doit être postérieure ou égale à la date de début.");
            }
            if (previousEnd != null && !update.startDate().isAfter(previousEnd)) {
                throw badRequest("Les semaines doivent être ordonnées et ne pas se chevaucher.");
            }
            previousEnd = update.endDate();
        }
    }

    private static boolean sameContent(TrainingPeriod period, PeriodUpdate update) {
        if (!Objects.equals(period.getStartDate(), update.startDate())
                || !Objects.equals(period.getEndDate(), update.endDate())) return false;
        for (int i = 0; i < period.getCompetencies().size(); i++) {
            TrainingCompetency competency = period.getCompetencies().get(i);
            CompetencyUpdate requested = update.competencies().get(i);
            if (!Objects.equals(competency.getRating(), requested.rating())
                    || !Objects.equals(competency.getComment(), clean(requested.comment()))) return false;
        }
        return true;
    }

    private static boolean isApproved(TrainingPeriod period) {
        return period.getTrainerSignedAt() != null || period.getTeamLeaderSignedAt() != null;
    }

    private static void requireReadyForWeeklyApproval(TrainingSheet sheet, TrainingPeriod current) {
        if (isApproved(current)) throw badRequest("Cette semaine est déjà validée par la formatrice ou le Team Leader.");
        for (TrainingPeriod period : sheet.getPeriods()) {
            if (period.getSequenceNo() >= current.getSequenceNo()) break;
            if (!isApproved(period)) throw badRequest("Validez la semaine précédente avant de continuer.");
        }
        if (current.getStartDate() == null || current.getEndDate() == null) {
            throw badRequest("Enregistrez les dates de la semaine avant sa validation.");
        }
        if (current.getCompetencies().size() != 26
                || current.getCompetencies().stream().anyMatch(c -> c.getRating() == null)) {
            throw badRequest("Les 26 critères doivent être évalués avant la validation de la semaine.");
        }
    }

    private static void requireEditor(TrainingSheet sheet, AuthUser actor) {
        requireRole(actor, Role.SUPER_ADMIN, Role.ADMIN, Role.FORMATRICE, Role.TEAM_LEADER);
        if (actor.role() == Role.FORMATRICE) requireAssignedTrainer(sheet, actor);
        if (actor.role() == Role.TEAM_LEADER) requireAssignedLeader(sheet, actor);
    }

    private static void requireAssignedTrainer(TrainingSheet sheet, AuthUser actor) {
        if (sheet.getCandidate().getTrainer() == null || !sheet.getCandidate().getTrainer().getId().equals(actor.id())) {
            throw forbidden("Seule la formatrice affectée peut évaluer et valider cette fiche.");
        }
    }

    private static void requireAssignedLeader(TrainingSheet sheet, AuthUser actor) {
        if (sheet.getCandidate().getTeamLeader() == null || !sheet.getCandidate().getTeamLeader().getId().equals(actor.id())) {
            throw forbidden("Seul le Team Leader affecté peut évaluer et valider cette fiche.");
        }
    }

    private static void requireRole(AuthUser actor, Role... allowed) {
        for (Role role : allowed) if (actor.role() == role) return;
        throw forbidden("Vous n'avez pas le rôle requis pour cette action.");
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

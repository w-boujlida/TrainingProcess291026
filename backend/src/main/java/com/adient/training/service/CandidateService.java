package com.adient.training.service;

import com.adient.training.dto.RecruitmentDtos.*;
import com.adient.training.entity.*;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.CandidateRepository;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CandidateService {

    /** Module team leaders and the HR "Team leader" job accounts can both lead a dossier. */
    public static final List<Role> TEAM_LEADER_ROLES = List.of(Role.TEAM_LEADER, Role.JOB_TEAM_LEADER);

    private final CandidateRepository candidates;
    private final UserRepository users;

    public CandidateService(CandidateRepository candidates, UserRepository users) {
        this.candidates = candidates;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<CandidateDto> list(RecruitmentCategory category) {
        List<Candidate> result = category == null
                ? candidates.findAllByOrderByCreatedAtDesc()
                : candidates.findAllByCategoryOrderByCreatedAtDesc(category);
        return result.stream().map(CandidateDto::of).toList();
    }

    @Transactional(readOnly = true)
    public CandidateDto get(Long id) {
        return CandidateDto.of(find(id));
    }

    @Transactional(readOnly = true)
    public List<StaffDto> staff() {
        return users.findAllByRoleInAndActiveTrueOrderByFullNameAsc(List.of(Role.FORMATRICE, Role.TEAM_LEADER, Role.JOB_TEAM_LEADER))
                .stream().map(StaffDto::of).toList();
    }

    @Transactional
    public CandidateDto create(AuthUser actor, CandidateRequest req) {
        String cin = clean(req.cin());
        String matricule = clean(req.matricule());
        if (candidates.existsByCinIgnoreCase(cin)) {
            throw new ApiException(HttpStatus.CONFLICT, "Un candidat avec ce CIN existe déjà.");
        }
        if (matricule != null && candidates.existsByMatriculeIgnoreCase(matricule)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce matricule est déjà attribué.");
        }
        Candidate c = new Candidate();
        apply(c, req);
        c.setStatus(req.status() == null ? CandidateStatus.CANDIDAT : req.status());
        c.setCreatedBy(actor.username());
        promoteIfValidated(c, null);
        return CandidateDto.of(candidates.save(c));
    }

    @Transactional
    public CandidateDto update(Long id, CandidateRequest req) {
        Candidate c = find(id);
        String cin = clean(req.cin());
        String matricule = clean(req.matricule());
        if (candidates.existsByCinIgnoreCaseAndIdNot(cin, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Un candidat avec ce CIN existe déjà.");
        }
        if (matricule != null && candidates.existsByMatriculeIgnoreCaseAndIdNot(matricule, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce matricule est déjà attribué.");
        }
        apply(c, req);
        if (req.status() != null) c.setStatus(req.status());
        promoteIfValidated(c, null);
        return CandidateDto.of(c);
    }

    @Transactional
    public CandidateDto setStatus(Long id, CandidateStatus status, String directPosition) {
        Candidate c = find(id);
        c.setStatus(status);
        promoteIfValidated(c, directPosition);
        return CandidateDto.of(c);
    }

    /**
     * Workflow: a Formation employee who is validated moves straight to Direct.
     * Direct requires a position from its fixed list: the given one, else the current position if it matches.
     */
    private void promoteIfValidated(Candidate c, String directPosition) {
        if (c.getCategory() != RecruitmentCategory.FORMATION || c.getStatus() != CandidateStatus.VALIDE) return;
        RecruitmentCategory target = RecruitmentCategory.DIRECT;
        String wanted = clean(directPosition) != null ? clean(directPosition) : c.getPosition();
        String resolved = target.resolvePosition(wanted).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                wanted == null
                        ? "Pour valider et passer en Direct, choisissez le poste Direct."
                        : "Pour valider et passer en Direct, choisissez un poste Direct (« " + wanted + " » n'en fait pas partie)."));
        c.setCategory(target);
        c.setPosition(resolved);
        c.setFormationValidatedOn(LocalDate.now());
    }

    @Transactional
    public List<CandidateDto> assign(AssignmentRequest req) {
        if (!req.changeShift() && !req.changeTeamLeader())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choisissez un shift ou un team leader à affecter.");
        Set<Long> wanted = new LinkedHashSet<>(req.ids());
        List<Candidate> found = candidates.findAllById(wanted);
        if (found.size() != wanted.size()) throw new ApiException(HttpStatus.NOT_FOUND, "Dossier introuvable.");
        String leaderMatricule = req.changeTeamLeader() && req.teamLeaderId() != null
                ? users.findById(req.teamLeaderId()).map(AppUser::getUsername).orElse(null) : null;
        if (leaderMatricule != null && found.stream().anyMatch(c -> leaderMatricule.equalsIgnoreCase(c.getMatricule())))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Un team leader ne peut pas être son propre team leader.");
        for (Candidate c : found) {
            if (req.changeShift()) c.setShift(req.shift());
            if (req.changeTeamLeader()) {
                setTeamLeader(c, req.teamLeaderId());
                // Confirming the HR suggestion makes it a manual choice, kept on the next HR imports.
                c.setTeamLeaderAuto(false);
            }
        }
        return found.stream().map(CandidateDto::of).toList();
    }

    @Transactional
    public void delete(Long id) {
        candidates.delete(find(id));
    }

    private void apply(Candidate c, CandidateRequest req) {
        c.setCategory(req.category());
        c.setMatricule(clean(req.matricule()));
        c.setFullName(req.fullName().trim());
        c.setCin(clean(req.cin()));
        c.setBirthDate(req.birthDate());
        c.setPhone(clean(req.phone()));
        c.setEmail(clean(req.email()));
        c.setAddress(clean(req.address()));
        c.setPosition(req.category() == RecruitmentCategory.FORMATION
                ? clean(req.position()) : position(req.category(), req.position()));
        c.setDepartment(clean(req.department()));
        c.setHireDate(req.hireDate());
        c.setEducationLevel(clean(req.educationLevel()));
        c.setShift(req.shift());
        c.setTrainer(staffMember(req.trainerId(), List.of(Role.FORMATRICE), "La formatrice sélectionnée est invalide."));
        setTeamLeader(c, req.teamLeaderId());
        c.setRemarks(clean(req.remarks()));
        if (req.category().requiresTrainingLevel()) {
            if (req.trainingLevel() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Le niveau (Apprentie, Junior ou Confirmée) est obligatoire.");
            }
            c.setTrainingLevel(req.trainingLevel());
        }
        // Other categories keep the stored level as history (e.g. employees promoted from Formation).
    }

    private static String position(RecruitmentCategory category, String value) {
        String v = clean(value);
        if (!category.hasFixedPositions()) return v;
        if (v == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le poste est obligatoire pour la catégorie " + category.getLabel() + ".");
        }
        return category.resolvePosition(v).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                "Poste invalide pour la catégorie " + category.getLabel() + " : « " + v + " »."));
    }

    /** A manual change of team leader stops the automatic HR linking for this dossier. */
    private void setTeamLeader(Candidate c, Long teamLeaderId) {
        Long current = c.getTeamLeader() == null ? null : c.getTeamLeader().getId();
        if (java.util.Objects.equals(current, teamLeaderId)) return;
        AppUser leader = staffMember(teamLeaderId, TEAM_LEADER_ROLES, "Le team leader sélectionné est invalide.");
        if (leader != null && !leader.isActive()) throw new ApiException(HttpStatus.BAD_REQUEST, "Le team leader sélectionné est invalide.");
        c.setTeamLeader(leader);
        c.setTeamLeaderAuto(false);
    }

    private AppUser staffMember(Long id, List<Role> expected, String error) {
        if (id == null) return null;
        AppUser u = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, error));
        if (!expected.contains(u.getRole())) throw new ApiException(HttpStatus.BAD_REQUEST, error);
        return u;
    }

    private Candidate find(Long id) {
        return candidates.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Candidat introuvable."));
    }

    private static String clean(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}

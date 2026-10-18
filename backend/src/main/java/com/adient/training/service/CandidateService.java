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

import java.util.List;

@Service
public class CandidateService {

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
        return users.findAllByRoleInAndActiveTrueOrderByFullNameAsc(List.of(Role.FORMATRICE, Role.TEAM_LEADER))
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
        return CandidateDto.of(c);
    }

    @Transactional
    public CandidateDto setStatus(Long id, CandidateStatus status) {
        Candidate c = find(id);
        c.setStatus(status);
        return CandidateDto.of(c);
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
        c.setPosition(clean(req.position()));
        c.setDepartment(clean(req.department()));
        c.setHireDate(req.hireDate());
        c.setEducationLevel(clean(req.educationLevel()));
        c.setShift(req.shift());
        c.setTrainer(staffMember(req.trainerId(), Role.FORMATRICE, "La formatrice sélectionnée est invalide."));
        c.setTeamLeader(staffMember(req.teamLeaderId(), Role.TEAM_LEADER, "Le team leader sélectionné est invalide."));
        c.setRemarks(clean(req.remarks()));
    }

    private AppUser staffMember(Long id, Role expected, String error) {
        if (id == null) return null;
        AppUser u = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, error));
        if (u.getRole() != expected) throw new ApiException(HttpStatus.BAD_REQUEST, error);
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

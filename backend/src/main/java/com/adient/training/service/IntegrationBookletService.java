package com.adient.training.service;

import com.adient.training.dto.RecruitmentDtos.*;
import com.adient.training.entity.*;
import com.adient.training.exception.ApiException;
import com.adient.training.repository.CandidateRepository;
import com.adient.training.repository.IntegrationBookletRepository;
import com.adient.training.repository.UserRepository;
import com.adient.training.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Service
public class IntegrationBookletService {

    private static final Set<String> OBJECTIVE_KEYS = Set.of(
            "company", "safety", "workstation", "quality", "operations", "autonomy");

    private final IntegrationBookletRepository booklets;
    private final CandidateRepository candidates;
    private final UserRepository users;

    public IntegrationBookletService(IntegrationBookletRepository booklets,
                                     CandidateRepository candidates,
                                     UserRepository users) {
        this.booklets = booklets;
        this.candidates = candidates;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public IntegrationBookletDto get(Long candidateId) {
        return IntegrationBookletDto.of(find(candidateId));
    }

    @Transactional
    public IntegrationBookletDto update(Long candidateId, IntegrationBookletUpdateRequest request) {
        IntegrationBooklet booklet = find(candidateId);
        if (request.trainingStartDate() != null && request.trainingEndDate() != null
                && request.trainingEndDate().isBefore(request.trainingStartDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La date de fin doit être après la date de début.");
        }

        Map<String, BookletObjectiveUpdate> incoming = new HashMap<>();
        for (BookletObjectiveUpdate objective : request.objectives()) {
            if (objective == null || objective.key() == null || objective.assessment() == null
                    || !OBJECTIVE_KEYS.contains(objective.key())
                    || incoming.put(objective.key(), objective) != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Les objectifs du livret sont invalides.");
            }
        }
        if (!incoming.keySet().equals(OBJECTIVE_KEYS)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tous les objectifs du livret doivent être fournis.");
        }

        Map<String, IntegrationBookletObjective> existing = new HashMap<>();
        booklet.getObjectives().forEach(o -> existing.put(o.getObjectiveKey(), o));
        if (!existing.keySet().equals(OBJECTIVE_KEYS)) {
            throw new ApiException(HttpStatus.CONFLICT, "Les objectifs du livret sont incomplets ; contactez l’administrateur.");
        }

        boolean changed = !java.util.Objects.equals(booklet.getTrainingStartDate(), request.trainingStartDate())
                || !java.util.Objects.equals(booklet.getTrainingEndDate(), request.trainingEndDate())
                || !java.util.Objects.equals(booklet.getEvaluationOutcome(), request.evaluationOutcome())
                || !java.util.Objects.equals(booklet.getEvaluationNotes(), clean(request.evaluationNotes()));
        for (Map.Entry<String, BookletObjectiveUpdate> entry : incoming.entrySet()) {
            IntegrationBookletObjective stored = existing.get(entry.getKey());
            BookletObjectiveUpdate received = entry.getValue();
            changed |= stored.getAssessment() != received.assessment()
                    || !java.util.Objects.equals(stored.getComment(), clean(received.comment()));
        }

        booklet.setTrainingStartDate(request.trainingStartDate());
        booklet.setTrainingEndDate(request.trainingEndDate());
        booklet.setEvaluationOutcome(request.evaluationOutcome());
        booklet.setEvaluationNotes(clean(request.evaluationNotes()));
        for (Map.Entry<String, BookletObjectiveUpdate> entry : incoming.entrySet()) {
            IntegrationBookletObjective stored = existing.get(entry.getKey());
            stored.setAssessment(entry.getValue().assessment());
            stored.setComment(clean(entry.getValue().comment()));
        }
        if (changed) clearSignatures(booklet);
        return IntegrationBookletDto.of(booklets.save(booklet));
    }

    @Transactional
    public IntegrationBookletDto signCandidate(Long candidateId, AuthUser actor, String signature) {
        IntegrationBooklet booklet = find(candidateId);
        if (booklet.getCandidateSignedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Le candidat a déjà signé ce livret.");
        }
        validateReadyForSignature(booklet);
        validatePngSignature(signature);
        AppUser signer = users.findById(actor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session utilisateur invalide."));
        booklet.setCandidateSignature(signature);
        booklet.setCandidateSignedBy(signer);
        booklet.setCandidateSignedAt(LocalDateTime.now());
        return IntegrationBookletDto.of(booklets.save(booklet));
    }

    @Transactional
    public IntegrationBookletDto signTrainer(Long candidateId, AuthUser actor) {
        IntegrationBooklet booklet = find(candidateId);
        if (booklet.getCandidateSignedAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Le candidat doit signer avant la formatrice.");
        }
        if (booklet.getTrainerSignedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "La formatrice a déjà signé ce livret.");
        }
        Candidate candidate = booklet.getCandidate();
        if (actor.role() != Role.FORMATRICE || candidate.getTrainer() == null
                || !candidate.getTrainer().getId().equals(actor.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Seule la formatrice affectée à ce candidat peut signer.");
        }
        AppUser trainer = users.findById(actor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session utilisateur invalide."));
        booklet.setTrainerSignedBy(trainer);
        booklet.setTrainerSignedAt(LocalDateTime.now());
        return IntegrationBookletDto.of(booklets.save(booklet));
    }

    private void validateReadyForSignature(IntegrationBooklet booklet) {
        if (booklet.getTrainingStartDate() == null || booklet.getTrainingEndDate() == null
                || booklet.getEvaluationOutcome() == null
                || booklet.getEvaluationOutcome() == BookletAssessment.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Renseignez les dates et l’évaluation finale avant la signature du candidat.");
        }
        if (booklet.getObjectives().stream().anyMatch(o -> o.getAssessment() == BookletAssessment.PENDING)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Évaluez chaque objectif avant la signature du candidat.");
        }
    }

    private static void validatePngSignature(String signature) {
        String prefix = "data:image/png;base64,";
        if (signature == null || !signature.startsWith(prefix)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La signature doit être une image PNG valide.");
        }
        try {
            byte[] image = Base64.getDecoder().decode(signature.substring(prefix.length()));
            byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
            if (image.length < png.length || image.length > 30_000) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "La signature doit être une image de moins de 30 Ko.");
            }
            for (int i = 0; i < png.length; i++) {
                if (image[i] != png[i]) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "La signature PNG est invalide.");
                }
            }
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "La signature PNG est invalide.");
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    if (!"png".equalsIgnoreCase(reader.getFormatName())
                            || reader.getWidth(0) > 1500 || reader.getHeight(0) > 400
                            || reader.read(0) == null) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "La signature PNG est invalide.");
                    }
                } finally {
                    reader.dispose();
                }
            }
        } catch (IllegalArgumentException | IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La signature PNG est invalide.");
        }
    }

    private static void clearSignatures(IntegrationBooklet booklet) {
        booklet.setCandidateSignature(null);
        booklet.setCandidateSignedBy(null);
        booklet.setCandidateSignedAt(null);
        booklet.setTrainerSignedBy(null);
        booklet.setTrainerSignedAt(null);
    }

    private IntegrationBooklet find(Long candidateId) {
        if (!candidates.existsById(candidateId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Candidat introuvable.");
        }
        return booklets.findByCandidateId(candidateId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Livret d’intégration introuvable."));
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}

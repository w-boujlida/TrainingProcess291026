package com.adient.training.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "integration_booklet")
public class IntegrationBooklet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private Candidate candidate;

    @Column(name = "training_start_date")
    private LocalDate trainingStartDate;

    @Column(name = "training_end_date")
    private LocalDate trainingEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_outcome", length = 30)
    private BookletAssessment evaluationOutcome;

    @Column(name = "evaluation_notes", length = 3000)
    private String evaluationNotes;

    @Column(name = "candidate_signature", columnDefinition = "TEXT")
    private String candidateSignature;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "candidate_signed_by")
    private AppUser candidateSignedBy;

    @Column(name = "candidate_signed_at")
    private LocalDateTime candidateSignedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "trainer_signed_by")
    private AppUser trainerSignedBy;

    @Column(name = "trainer_signed_at")
    private LocalDateTime trainerSignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "booklet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<IntegrationBookletObjective> objectives = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }
    public void setCandidate(Candidate candidate) { this.candidate = candidate; }
    public LocalDate getTrainingStartDate() { return trainingStartDate; }
    public void setTrainingStartDate(LocalDate trainingStartDate) { this.trainingStartDate = trainingStartDate; }
    public LocalDate getTrainingEndDate() { return trainingEndDate; }
    public void setTrainingEndDate(LocalDate trainingEndDate) { this.trainingEndDate = trainingEndDate; }
    public BookletAssessment getEvaluationOutcome() { return evaluationOutcome; }
    public void setEvaluationOutcome(BookletAssessment evaluationOutcome) { this.evaluationOutcome = evaluationOutcome; }
    public String getEvaluationNotes() { return evaluationNotes; }
    public void setEvaluationNotes(String evaluationNotes) { this.evaluationNotes = evaluationNotes; }
    public String getCandidateSignature() { return candidateSignature; }
    public void setCandidateSignature(String candidateSignature) { this.candidateSignature = candidateSignature; }
    public AppUser getCandidateSignedBy() { return candidateSignedBy; }
    public void setCandidateSignedBy(AppUser candidateSignedBy) { this.candidateSignedBy = candidateSignedBy; }
    public LocalDateTime getCandidateSignedAt() { return candidateSignedAt; }
    public void setCandidateSignedAt(LocalDateTime candidateSignedAt) { this.candidateSignedAt = candidateSignedAt; }
    public AppUser getTrainerSignedBy() { return trainerSignedBy; }
    public void setTrainerSignedBy(AppUser trainerSignedBy) { this.trainerSignedBy = trainerSignedBy; }
    public LocalDateTime getTrainerSignedAt() { return trainerSignedAt; }
    public void setTrainerSignedAt(LocalDateTime trainerSignedAt) { this.trainerSignedAt = trainerSignedAt; }
    public List<IntegrationBookletObjective> getObjectives() { return objectives; }
}

package com.adient.training.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "training_sheet")
public class TrainingSheet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supervisor_signed_by")
    private AppUser supervisorSignedBy;

    @Column(name = "supervisor_signed_at")
    private LocalDateTime supervisorSignedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "quality_signed_by")
    private AppUser qualitySignedBy;

    @Column(name = "quality_signed_at")
    private LocalDateTime qualitySignedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "behavior_opinion", length = 1000)
    private String behaviorOpinion;

    @Column(name = "knowledge_opinion", length = 1000)
    private String knowledgeOpinion;

    @Column(name = "machine_opinion", length = 1000)
    private String machineOpinion;

    @Column(name = "sewing_opinion", length = 1000)
    private String sewingOpinion;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceNo ASC")
    private List<TrainingPeriod> periods = new ArrayList<>();

    @PrePersist
    void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public void touch() { updatedAt = LocalDateTime.now(); }
    public Candidate getCandidate() { return candidate; }
    public void setCandidate(Candidate candidate) { this.candidate = candidate; }
    public AppUser getSupervisorSignedBy() { return supervisorSignedBy; }
    public void setSupervisorSignedBy(AppUser supervisorSignedBy) { this.supervisorSignedBy = supervisorSignedBy; }
    public LocalDateTime getSupervisorSignedAt() { return supervisorSignedAt; }
    public void setSupervisorSignedAt(LocalDateTime supervisorSignedAt) { this.supervisorSignedAt = supervisorSignedAt; }
    public AppUser getQualitySignedBy() { return qualitySignedBy; }
    public void setQualitySignedBy(AppUser qualitySignedBy) { this.qualitySignedBy = qualitySignedBy; }
    public LocalDateTime getQualitySignedAt() { return qualitySignedAt; }
    public void setQualitySignedAt(LocalDateTime qualitySignedAt) { this.qualitySignedAt = qualitySignedAt; }
    public List<TrainingPeriod> getPeriods() { return periods; }
    public String getBehaviorOpinion() { return behaviorOpinion; }
    public void setBehaviorOpinion(String opinion) { behaviorOpinion = opinion; }
    public String getKnowledgeOpinion() { return knowledgeOpinion; }
    public void setKnowledgeOpinion(String opinion) { knowledgeOpinion = opinion; }
    public String getMachineOpinion() { return machineOpinion; }
    public void setMachineOpinion(String opinion) { machineOpinion = opinion; }
    public String getSewingOpinion() { return sewingOpinion; }
    public void setSewingOpinion(String opinion) { sewingOpinion = opinion; }
}

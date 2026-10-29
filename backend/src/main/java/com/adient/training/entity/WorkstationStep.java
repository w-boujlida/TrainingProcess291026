package com.adient.training.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "workstation_step")
public class WorkstationStep {
    public enum TrainingStatus { X, NA }
    public enum EvaluationStatus { OK, NOK }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private WorkstationCycle cycle;
    private int sequenceNo;
    @Column(nullable = false, length = 500)
    private String label;
    @Column(nullable = false, length = 10)
    private String applicability;
    @Column(nullable = false, length = 100)
    private String concernedPositions;
    private LocalDate deadline;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private TrainingStatus trainingStatus;
    @Enumerated(EnumType.STRING) @Column(length = 10)
    private EvaluationStatus evaluationStatus;
    @Column(length = 120)
    private String signedBy;
    private LocalDateTime signedAt;

    public Long getId() { return id; }
    public int getSequenceNo() { return sequenceNo; }
    public String getLabel() { return label; }
    public String getApplicability() { return applicability; }
    public String getConcernedPositions() { return concernedPositions; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate date) { deadline = date; }
    public TrainingStatus getTrainingStatus() { return trainingStatus; }
    public void setTrainingStatus(TrainingStatus value) { trainingStatus = value; }
    public EvaluationStatus getEvaluationStatus() { return evaluationStatus; }
    public void setEvaluationStatus(EvaluationStatus value) { evaluationStatus = value; }
    public String getSignedBy() { return signedBy; }
    public void setSignedBy(String value) { signedBy = value; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime value) { signedAt = value; }
}

package com.adient.training.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workstation_cycle")
public class WorkstationCycle {
    public enum Kind { INITIAL, REFRESHER }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_id", nullable = false)
    private WorkstationForm form;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Kind kind;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate evaluationDate;
    @Column(length = 3000)
    private String measures;
    @Column(columnDefinition = "text")
    private String operatorSignature;
    private LocalDateTime operatorSignedAt;
    @Column(length = 120)
    private String operatorRecordedBy;
    @OneToMany(mappedBy = "cycle", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<WorkstationStep> steps = new ArrayList<>();

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate date) { startDate = date; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate date) { endDate = date; }
    public LocalDate getEvaluationDate() { return evaluationDate; }
    public void setEvaluationDate(LocalDate date) { evaluationDate = date; }
    public String getMeasures() { return measures; }
    public void setMeasures(String value) { measures = value; }
    public String getOperatorSignature() { return operatorSignature; }
    public void setOperatorSignature(String value) { operatorSignature = value; }
    public LocalDateTime getOperatorSignedAt() { return operatorSignedAt; }
    public void setOperatorSignedAt(LocalDateTime value) { operatorSignedAt = value; }
    public String getOperatorRecordedBy() { return operatorRecordedBy; }
    public void setOperatorRecordedBy(String value) { operatorRecordedBy = value; }
    public List<WorkstationStep> getSteps() { return steps; }
}

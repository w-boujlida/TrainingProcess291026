package com.adient.training.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "training_period")
public class TrainingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private TrainingSheet sheet;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "trainer_signed_by")
    private AppUser trainerSignedBy;

    @Column(name = "trainer_signed_at")
    private LocalDateTime trainerSignedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "team_leader_signed_by")
    private AppUser teamLeaderSignedBy;

    @Column(name = "team_leader_signed_at")
    private LocalDateTime teamLeaderSignedAt;

    @OneToMany(mappedBy = "period", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceNo ASC")
    private List<TrainingCompetency> competencies = new ArrayList<>();

    public Long getId() { return id; }
    public TrainingSheet getSheet() { return sheet; }
    public void setSheet(TrainingSheet sheet) { this.sheet = sheet; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public AppUser getTrainerSignedBy() { return trainerSignedBy; }
    public void setTrainerSignedBy(AppUser trainerSignedBy) { this.trainerSignedBy = trainerSignedBy; }
    public LocalDateTime getTrainerSignedAt() { return trainerSignedAt; }
    public void setTrainerSignedAt(LocalDateTime trainerSignedAt) { this.trainerSignedAt = trainerSignedAt; }
    public AppUser getTeamLeaderSignedBy() { return teamLeaderSignedBy; }
    public void setTeamLeaderSignedBy(AppUser teamLeaderSignedBy) { this.teamLeaderSignedBy = teamLeaderSignedBy; }
    public LocalDateTime getTeamLeaderSignedAt() { return teamLeaderSignedAt; }
    public void setTeamLeaderSignedAt(LocalDateTime teamLeaderSignedAt) { this.teamLeaderSignedAt = teamLeaderSignedAt; }
    public List<TrainingCompetency> getCompetencies() { return competencies; }
}

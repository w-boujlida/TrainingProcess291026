package com.adient.training.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "training_competency")
public class TrainingCompetency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    private TrainingPeriod period;

    @Column(nullable = false, length = 300)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TrainingSection section;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    private Integer rating;

    @Column(length = 1000)
    private String comment;

    public Long getId() { return id; }
    public TrainingPeriod getPeriod() { return period; }
    public void setPeriod(TrainingPeriod period) { this.period = period; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public TrainingSection getSection() { return section; }
    public void setSection(TrainingSection section) { this.section = section; }
    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

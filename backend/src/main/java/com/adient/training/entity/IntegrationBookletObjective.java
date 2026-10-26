package com.adient.training.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "integration_booklet_objective")
public class IntegrationBookletObjective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booklet_id", nullable = false)
    private IntegrationBooklet booklet;

    @Column(name = "objective_key", nullable = false, length = 40)
    private String objectiveKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookletAssessment assessment = BookletAssessment.PENDING;

    @Column(length = 1000)
    private String comment;

    public Long getId() { return id; }
    public IntegrationBooklet getBooklet() { return booklet; }
    public void setBooklet(IntegrationBooklet booklet) { this.booklet = booklet; }
    public String getObjectiveKey() { return objectiveKey; }
    public void setObjectiveKey(String objectiveKey) { this.objectiveKey = objectiveKey; }
    public BookletAssessment getAssessment() { return assessment; }
    public void setAssessment(BookletAssessment assessment) { this.assessment = assessment; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

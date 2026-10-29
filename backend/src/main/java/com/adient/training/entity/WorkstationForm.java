package com.adient.training.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workstation_form")
public class WorkstationForm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private long version;
    @OneToOne(optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true)
    private Candidate candidate;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("kind ASC")
    private List<WorkstationCycle> cycles = new ArrayList<>();

    public Long getId() { return id; }
    public long getVersion() { return version; }
    public Candidate getCandidate() { return candidate; }
    public List<WorkstationCycle> getCycles() { return cycles; }
    public void touch() { updatedAt = LocalDateTime.now(); }
}

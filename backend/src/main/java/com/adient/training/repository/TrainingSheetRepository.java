package com.adient.training.repository;

import com.adient.training.entity.TrainingSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrainingSheetRepository extends JpaRepository<TrainingSheet, Long> {
    Optional<TrainingSheet> findByCandidateId(Long candidateId);
}

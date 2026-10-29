package com.adient.training.repository;

import com.adient.training.entity.WorkstationForm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorkstationFormRepository extends JpaRepository<WorkstationForm, Long> {
    Optional<WorkstationForm> findByCandidateId(Long candidateId);
}

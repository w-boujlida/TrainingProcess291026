package com.adient.training.repository;

import com.adient.training.entity.IntegrationBooklet;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationBookletRepository extends JpaRepository<IntegrationBooklet, Long> {
    @EntityGraph(attributePaths = {"candidate", "objectives"})
    Optional<IntegrationBooklet> findByCandidateId(Long candidateId);
}

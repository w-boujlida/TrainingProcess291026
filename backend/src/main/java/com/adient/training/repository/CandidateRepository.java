package com.adient.training.repository;

import com.adient.training.entity.Candidate;
import com.adient.training.entity.RecruitmentCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findAllByCategoryOrderByCreatedAtDesc(RecruitmentCategory category);
    List<Candidate> findAllByOrderByCreatedAtDesc();
    boolean existsByCinIgnoreCase(String cin);
    java.util.Optional<Candidate> findByCinIgnoreCase(String cin);
    boolean existsByCinIgnoreCaseAndIdNot(String cin, Long id);
    boolean existsByMatriculeIgnoreCase(String matricule);
    boolean existsByMatriculeIgnoreCaseAndIdNot(String matricule, Long id);
}

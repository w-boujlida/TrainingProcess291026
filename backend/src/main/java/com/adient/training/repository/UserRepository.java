package com.adient.training.repository;

import com.adient.training.entity.AppUser;
import com.adient.training.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByRole(Role role);
    long countByRoleAndActiveTrue(Role role);
    List<AppUser> findAllByOrderByFullNameAsc();
    List<AppUser> findAllByRoleInAndActiveTrueOrderByFullNameAsc(List<Role> roles);
}

package com.cloudbite.repository;

import com.cloudbite.enums.Role;
import com.cloudbite.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(Role role);
    List<User> findByRoleAndIsActive(Role role, Boolean isActive);
    List<User> findByRoleAndIsAvailable(Role role, Boolean isAvailable);
    long countByRole(Role role);
}

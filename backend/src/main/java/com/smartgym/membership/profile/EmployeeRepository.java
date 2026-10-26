package com.smartgym.membership.profile;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
    @Query("select employee from Employee employee "
            + "where lower(employee.keycloakUserId) = lower(:keycloakUserId)")
    Optional<Employee> findByKeycloakUserId(@Param("keycloakUserId") String keycloakUserId);

    @Query("select employee from Employee employee "
            + "where lower(employee.keycloakUserId) in :keycloakUserIds")
    List<Employee> findAllByKeycloakUserIdIn(@Param("keycloakUserIds") Collection<String> keycloakUserIds);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);
}

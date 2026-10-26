package com.smartgym.courses;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Page<Course> findByActiveTrue(Pageable pageable);

    Page<Course> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Course> findAllByActiveTrueOrderByCreatedAtDesc(Pageable pageable);

    Optional<Course> findByNameIgnoreCase(String name);

    @Query("select c from Course c where lower(c.name) like lower(concat('%', :query, '%')) or lower(c.description) like lower(concat('%', :query, '%'))")
    Page<Course> search(@Param("query") String query, Pageable pageable);

    @Query("select c from Course c where c.active = true and " +
            "(lower(c.name) like lower(concat('%', :query, '%')) or lower(c.description) like lower(concat('%', :query, '%')))")
    Page<Course> searchActive(@Param("query") String query, Pageable pageable);
}

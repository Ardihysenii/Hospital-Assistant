package com.assistant.hospitalassistantbackend.repositories;

import com.assistant.hospitalassistantbackend.models.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    // Spring Data JPA derives the query from the "bloodGroup" field name automatically
    List<Patient> findByBloodGroup(String bloodGroup);

    Optional<Patient> findByUserUsername(String username);


    // ADD THIS LINE TO FIX THE COMPILATION ERROR
    Optional<Patient> findByFirstName(String firstName);
}
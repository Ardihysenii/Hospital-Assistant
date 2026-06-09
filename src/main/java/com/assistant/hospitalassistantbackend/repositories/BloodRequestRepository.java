package com.assistant.hospitalassistantbackend.repositories;

import com.assistant.hospitalassistantbackend.models.BloodRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {
    List<BloodRequest> findByBloodGroupRequestedAndStatus(String bloodGroup, String status);
}
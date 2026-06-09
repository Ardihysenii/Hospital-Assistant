package com.assistant.hospitalassistantbackend.services;

import com.assistant.hospitalassistantbackend.dto.BloodRequestDTO;
import com.assistant.hospitalassistantbackend.models.BloodRequest;
import com.assistant.hospitalassistantbackend.models.Patient;
import com.assistant.hospitalassistantbackend.repositories.BloodRequestRepository;
import com.assistant.hospitalassistantbackend.repositories.PatientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BloodRequestService {

    private final BloodRequestRepository bloodRequestRepository;
    private final PatientRepository patientRepository;

    public BloodRequestService(BloodRequestRepository bloodRequestRepository,
                               PatientRepository patientRepository) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.patientRepository = patientRepository;
    }

    public BloodRequest createEmergencyRequest(BloodRequestDTO dto) {
        BloodRequest request = new BloodRequest();
        request.setPatientName(dto.getPatientName());
        // Clean input data on save
        request.setBloodGroupRequested(dto.getBloodGroupRequested().trim().toUpperCase());
        request.setUrgencyMessage(dto.getUrgencyMessage());
        request.setStatus("PENDING");
        return bloodRequestRepository.save(request);
    }

    public List<BloodRequest> getRequestsForBloodGroup(String bloodGroup) {
        return bloodRequestRepository.findByBloodGroupRequestedAndStatus(bloodGroup.trim().toUpperCase(), "PENDING");
    }

    public List<BloodRequest> getRequestsForUser(String username) {
        Patient patient = patientRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found"));

        // CRITICAL FIX: Trim whitespace and force uppercase to match DB text exactly
        String cleanBloodGroup = patient.getBloodGroup().trim().toUpperCase();

        return bloodRequestRepository.findByBloodGroupRequestedAndStatus(
                cleanBloodGroup, "PENDING"
        );
    }

    public BloodRequest acceptRequest(Long requestId, String username) {
        BloodRequest request = bloodRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        Patient patient = patientRepository.findByUserUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient profile not found for this user"));

        request.setStatus("ACCEPTED");
        request.setAcceptedBy(patient);
        return bloodRequestRepository.save(request);
    }
}
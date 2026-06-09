package com.assistant.hospitalassistantbackend.controllers;

import com.assistant.hospitalassistantbackend.dto.BloodRequestDTO;
import com.assistant.hospitalassistantbackend.dto.BloodRequestResponseDTO;
import com.assistant.hospitalassistantbackend.models.BloodRequest;
import com.assistant.hospitalassistantbackend.services.BloodRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/blood-requests")
public class BloodRequestController {

    private final BloodRequestService bloodRequestService;

    public BloodRequestController(BloodRequestService bloodRequestService) {
        this.bloodRequestService = bloodRequestService;
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasAnyRole('ADMIN', 'NURSE')")
    public ResponseEntity<BloodRequest> broadcastEmergency(@RequestBody BloodRequestDTO dto) {
        return ResponseEntity.ok(bloodRequestService.createEmergencyRequest(dto));
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<BloodRequest>> getMyRequests(Principal principal) {
        // principal.getName() = logged in username
        // We need their blood group from their patient profile
        return ResponseEntity.ok(bloodRequestService.getRequestsForUser(principal.getName()));
    }

    @PostMapping("/accept/{requestId}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<BloodRequest> acceptRequest(@PathVariable Long requestId, Principal principal) {
        return ResponseEntity.ok(bloodRequestService.acceptRequest(requestId, principal.getName()));
    }
}
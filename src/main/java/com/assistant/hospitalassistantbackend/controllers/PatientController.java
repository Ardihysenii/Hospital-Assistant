package com.assistant.hospitalassistantbackend.controllers;

import com.assistant.hospitalassistantbackend.models.Patient;
import com.assistant.hospitalassistantbackend.models.User;
import com.assistant.hospitalassistantbackend.repositories.PatientRepository;
import com.assistant.hospitalassistantbackend.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    public PatientController(
            PatientRepository patientRepository,
            UserRepository userRepository) {
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Patient> getPatientById(@PathVariable Long id) {
        return patientRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createPatient(@RequestBody Patient patient) {

        if (patient.getUser() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing user object"));
        }

        if (patient.getUser().getId() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing user.id"));
        }

        User user = userRepository.findById(patient.getUser().getId())
                .orElse(null);

        if (user == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "User not found with id: " + patient.getUser().getId()));
        }

        patient.setUser(user);

        Patient savedPatient = patientRepository.save(patient);

        return ResponseEntity.ok(savedPatient);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Patient> updatePatient(
            @PathVariable Long id,
            @RequestBody Patient patientDetails) {

        return patientRepository.findById(id)
                .map(patient -> {
                    patient.setFirstName(patientDetails.getFirstName());
                    patient.setLastName(patientDetails.getLastName());
                    patient.setEmail(patientDetails.getEmail());
                    patient.setPhoneNumber(patientDetails.getPhoneNumber());
                    patient.setBloodGroup(patientDetails.getBloodGroup());
                    patient.setDateOfBirth(patientDetails.getDateOfBirth());

                    Patient updatedPatient = patientRepository.save(patient);

                    return ResponseEntity.ok(updatedPatient);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deletePatient(@PathVariable Long id) {
        return patientRepository.findById(id)
                .map(patient -> {
                    patientRepository.delete(patient);
                    return ResponseEntity.ok(Map.of("deleted", Boolean.TRUE));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
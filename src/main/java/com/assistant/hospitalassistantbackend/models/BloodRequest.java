package com.assistant.hospitalassistantbackend.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "blood_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BloodRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_name", nullable = false, length = 100)
    private String patientName;

    @Column(name = "blood_group_requested", nullable = false, length = 50)
    private String bloodGroupRequested;

    @Column(name = "urgency_message", length = 500)
    private String urgencyMessage;

    @Column(name = "status", length = 20)
    private String status = "PENDING";

    @ManyToOne
    @JoinColumn(name = "accepted_by_patient_id")
    private Patient acceptedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
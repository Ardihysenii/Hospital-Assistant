package com.assistant.hospitalassistantbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BloodRequestDTO {
    private String patientName;
    private String bloodGroupRequested; // Matches selection like "A+", "O-", etc.
    private String urgencyMessage;
}
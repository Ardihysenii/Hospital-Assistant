package com.assistant.hospitalassistantbackend.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Set;

@Data
public class RegisterRequest {

    private String username;
    private String email;
    private String password;
    private Set<String> roles;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String bloodGroup;
    private LocalDate dateOfBirth;
}
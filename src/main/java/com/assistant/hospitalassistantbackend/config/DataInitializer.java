package com.assistant.hospitalassistantbackend.config;

import com.assistant.hospitalassistantbackend.models.Role;
import com.assistant.hospitalassistantbackend.models.User;
import com.assistant.hospitalassistantbackend.repositories.RoleRepository;
import com.assistant.hospitalassistantbackend.repositories.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository,
                                   RoleRepository roleRepository,
                                   PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Ensure essential system roles exist in your roles database table
            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setName("ROLE_ADMIN");
                        return roleRepository.save(role);
                    });

            Role nurseRole = roleRepository.findByName("ROLE_NURSE")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setName("ROLE_NURSE");
                        return roleRepository.save(role);
                    });

            Role patientRole = roleRepository.findByName("ROLE_PATIENT")
                    .orElseGet(() -> {
                        Role role = new Role();
                        role.setName("ROLE_PATIENT");
                        return roleRepository.save(role);
                    });

            // 2. Inject a brand new, known Master Admin if it doesn't exist yet
            if (!userRepository.existsByUsername("master_admin")) {
                User admin = new User();
                admin.setUsername("master_admin");
                admin.setEmail("admin@hospital.com");

                // This hashes your password properly using BCrypt
                admin.setPassword(passwordEncoder.encode("Password123!"));

                Set<Role> roles = new HashSet<>();
                roles.add(adminRole);
                admin.setRoles(roles);

                userRepository.save(admin);
                System.out.println(">>>> DATABASE SEEDED: Created user 'master_admin' with password 'Password123!' <<<<");
            }
        };
    }
}
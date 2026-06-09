package com.assistant.hospitalassistantbackend.controllers;

import com.assistant.hospitalassistantbackend.dto.LoginRequest;
import com.assistant.hospitalassistantbackend.dto.RegisterRequest;
import com.assistant.hospitalassistantbackend.models.Patient;
import com.assistant.hospitalassistantbackend.models.Role;
import com.assistant.hospitalassistantbackend.models.User;
import com.assistant.hospitalassistantbackend.repositories.PatientRepository;
import com.assistant.hospitalassistantbackend.repositories.RoleRepository;
import com.assistant.hospitalassistantbackend.repositories.UserRepository;
import com.assistant.hospitalassistantbackend.services.CustomUserDetailsService;
import com.assistant.hospitalassistantbackend.services.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;

    public AuthController(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PatientRepository patientRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            AuthenticationManager authenticationManager) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {

        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Username is already taken!")
            );
        }

        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", "Email is already in use!")
            );
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        Set<String> strRoles = registerRequest.getRoles();
        Set<Role> roles = new HashSet<>();

        if (strRoles == null || strRoles.isEmpty()) {

            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Role not found."));

            roles.add(userRole);

        } else {

            strRoles.forEach(role -> {
                switch (role.toLowerCase()) {

                    case "admin":
                        roles.add(roleRepository.findByName("ROLE_ADMIN")
                                .orElseThrow(() -> new RuntimeException("Role not found.")));
                        break;

                    case "nurse":
                        roles.add(roleRepository.findByName("ROLE_NURSE")
                                .orElseThrow(() -> new RuntimeException("Role not found.")));
                        break;

                    case "patient":
                        roles.add(roleRepository.findByName("ROLE_PATIENT")
                                .orElseThrow(() -> new RuntimeException("Role not found.")));
                        break;

                    default:
                        roles.add(roleRepository.findByName("ROLE_USER")
                                .orElseThrow(() -> new RuntimeException("Role not found.")));
                }
            });
        }

        user.setRoles(roles);

        User savedUser = userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User registered successfully!",
                "userId", savedUser.getId()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {

        try {

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            User user = userRepository
                    .findByUsername(loginRequest.getUsername())
                    .orElseThrow();

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(user.getUsername());

            String jwtToken = jwtService.generateToken(userDetails);

            String singleRole = user.getRoles()
                    .stream()
                    .findFirst()
                    .map(Role::getName)
                    .map(name -> name.replace("ROLE_", ""))
                    .orElse("USER");

            return ResponseEntity.ok(Map.of(
                    "token", jwtToken,
                    "username", user.getUsername(),
                    "role", singleRole
            ));

        } catch (Exception e) {

            return ResponseEntity.badRequest().body(
                    Map.of("message", "Login failed.")
            );
        }
    }
}
package com.assistant.hospitalassistantbackend.controllers;

import com.assistant.hospitalassistantbackend.models.Role;
import com.assistant.hospitalassistantbackend.models.User;
import com.assistant.hospitalassistantbackend.repositories.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        List<Map<String, Object>> users = userRepository.findAll()
                .stream()
                .map(user -> {
                    Map<String, Object> map = new java.util.HashMap<>();
                    map.put("id", user.getId());
                    map.put("username", user.getUsername());
                    map.put("email", user.getEmail());
                    map.put("role", user.getRoles().stream()
                            .findFirst()
                            .map(Role::getName)
                            .map(name -> name.replace("ROLE_", ""))
                            .orElse("UNKNOWN"));
                    return map;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }
}
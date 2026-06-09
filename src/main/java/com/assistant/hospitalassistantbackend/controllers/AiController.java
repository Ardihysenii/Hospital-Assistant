package com.assistant.hospitalassistantbackend.controllers;

import com.assistant.hospitalassistantbackend.services.AiCommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiCommandService aiCommandService;

    public AiController(AiCommandService aiCommandService) {
        this.aiCommandService = aiCommandService;
    }

    @PostMapping("/command")
    public ResponseEntity<Map<String, String>> processVoiceCommand(@RequestBody Map<String, String> request) {
        String rawCommand = request.get("command");
        String resultMessage = aiCommandService.executeVoiceCommand(rawCommand);
        return ResponseEntity.ok(Map.of("response", resultMessage));
    }
}
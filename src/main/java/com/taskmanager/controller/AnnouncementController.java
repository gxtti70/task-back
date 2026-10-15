package com.taskmanager.controller;

import com.taskmanager.dto.response.AnnouncementResponse;
import com.taskmanager.repository.UserRepository;
import com.taskmanager.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/announcements") @RequiredArgsConstructor
public class AnnouncementController {
    private final AnnouncementService service;
    private final UserRepository users;
    @GetMapping("/pending") public List<AnnouncementResponse> pending(Authentication auth) { return service.pending(currentId(auth)); }
    @PostMapping("/{key}/dismiss") @ResponseStatus(HttpStatus.NO_CONTENT) public void dismiss(@PathVariable String key, Authentication auth) { service.dismiss(currentId(auth), key); }
    private UUID currentId(Authentication auth) { return users.findByEmailIgnoreCase(auth.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId(); }
}

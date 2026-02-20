package com.solanatip.controller;

import com.solanatip.dto.CreatorDto;
import com.solanatip.entity.Creator;
import com.solanatip.exception.ApiExceptions;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.service.CreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class AdminController {

    private final CreatorRepository creatorRepository;
    private final CreatorService creatorService;

    @GetMapping("/creators")
    public ResponseEntity<List<AdminCreatorResponse>> listCreators(Authentication authentication) {
        assertAdmin(authentication);

        List<AdminCreatorResponse> creators = creatorRepository.findAll().stream()
                .map(this::toAdminResponse)
                .toList();

        return ResponseEntity.ok(creators);
    }

    @DeleteMapping("/creators/{username}")
    public ResponseEntity<Map<String, String>> deleteCreator(
            @PathVariable String username,
            Authentication authentication) {
        assertAdmin(authentication);
        creatorService.deleteCreator(username);
        return ResponseEntity.ok(Map.of("message", "Creator deleted: " + username));
    }

    @PostMapping("/creators/{username}/toggle-admin")
    public ResponseEntity<Map<String, Object>> toggleAdmin(
            @PathVariable String username,
            Authentication authentication) {
        assertAdmin(authentication);

        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Creator not found"));
        creator.setAdmin(!creator.isAdmin());
        creatorRepository.save(creator);

        return ResponseEntity.ok(Map.of(
                "username", username,
                "admin", creator.isAdmin()
        ));
    }

    private void assertAdmin(Authentication authentication) {
        String username = authentication.getName();
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ApiExceptions.ForbiddenException("Access denied"));
        if (!creator.isAdmin()) {
            throw new ApiExceptions.ForbiddenException("Admin access required");
        }
    }

    private AdminCreatorResponse toAdminResponse(Creator c) {
        return new AdminCreatorResponse(
                c.getId().toString(),
                c.getUsername(),
                c.getDisplayName(),
                c.getEmail(),
                c.getWalletAddress(),
                c.isEmailVerified(),
                c.isAdmin(),
                c.getAuthProvider().name(),
                c.getCreatedAt() != null ? c.getCreatedAt().toString() : null
        );
    }

    public record AdminCreatorResponse(
            String id, String username, String displayName, String email,
            String walletAddress, boolean emailVerified, boolean admin,
            String authProvider, String createdAt
    ) {}
}
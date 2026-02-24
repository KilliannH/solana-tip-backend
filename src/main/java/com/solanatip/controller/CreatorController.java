package com.solanatip.controller;

import com.solanatip.dto.CreatorDto;
import com.solanatip.service.CreatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/creators")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class CreatorController {

    private final CreatorService creatorService;

    @GetMapping("/{username}")
    public ResponseEntity<CreatorDto.Response> getCreator(@PathVariable String username) {
        return ResponseEntity.ok(creatorService.getCreatorByUsername(username));
    }

    @GetMapping
    public ResponseEntity<List<CreatorDto.Response>> getAllCreators() {
        return ResponseEntity.ok(creatorService.getAllCreators());
    }

    @GetMapping("/search")
    public ResponseEntity<Page<CreatorDto.Response>> searchCreators(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(creatorService.getAllCreatorsPaginated(page, size, q));
    }

    @PutMapping("/{username}")
    public ResponseEntity<CreatorDto.Response> updateCreator(
            @PathVariable String username,
            @Valid @RequestBody CreatorDto.UpdateRequest request,
            Authentication authentication) {
        assertOwnership(authentication, username);
        return ResponseEntity.ok(creatorService.updateCreator(username, request));
    }

    @PutMapping("/{username}/settings")
    public ResponseEntity<CreatorDto.Response> updateSettings(
            @PathVariable String username,
            @RequestBody CreatorDto.SettingsRequest request,
            Authentication authentication) {
        assertOwnership(authentication, username);
        return ResponseEntity.ok(creatorService.updateSettings(username, request));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteCreator(@PathVariable String username, Authentication authentication) {
        assertOwnership(authentication, username);
        creatorService.deleteCreator(username);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ensure the authenticated creator is modifying their own profile.
     */
    private void assertOwnership(Authentication authentication, String username) {
        String authenticatedUsername = authentication.getName();
        if (!authenticatedUsername.equals(username)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only modify your own profile");
        }
    }
}
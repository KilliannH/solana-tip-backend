package com.solanatip.controller;

import com.solanatip.dto.CreatorDto;
import com.solanatip.service.CreatorService;
import com.solanatip.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/creators/{username}")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class FileUploadController {

    private final S3Service s3Service;
    private final CreatorService creatorService;

    @PostMapping("/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @PathVariable String username,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        assertOwnership(authentication, username);

        String url = s3Service.uploadImage(file, "avatars", username);
        creatorService.updateCreator(username, CreatorDto.UpdateRequest.builder().avatarUrl(url).build());

        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping("/banner")
    public ResponseEntity<Map<String, String>> uploadBanner(
            @PathVariable String username,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        assertOwnership(authentication, username);

        String url = s3Service.uploadImage(file, "banners", username);
        creatorService.updateCreator(username, CreatorDto.UpdateRequest.builder().bannerUrl(url).build());

        return ResponseEntity.ok(Map.of("url", url));
    }

    private void assertOwnership(Authentication authentication, String username) {
        if (!authentication.getName().equals(username)) {
            throw new AccessDeniedException("You can only modify your own profile");
        }
    }
}
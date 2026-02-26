package com.solanatip.controller;

import com.solanatip.dto.AlertSettingsDto;
import com.solanatip.entity.Creator;
import com.solanatip.entity.SubscriptionPlan;
import com.solanatip.exception.ApiExceptions;
import com.solanatip.repository.CreatorRepository;
import com.solanatip.service.TipAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlertController {

    private final TipAlertService tipAlertService;
    private final CreatorRepository creatorRepository;

    /**
     * SSE endpoint for OBS overlay.
     */
    @GetMapping(value = "/{username}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String username) {
        return tipAlertService.subscribe(username);
    }

    /**
     * Get alert settings (public — overlay reads this).
     */
    @GetMapping("/{username}/settings")
    public ResponseEntity<AlertSettingsDto.Response> getSettings(@PathVariable String username) {
        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Creator not found"));

        // Free users get defaults
        boolean isPro = creator.getSubscriptionPlan() == SubscriptionPlan.PRO;

        return ResponseEntity.ok(AlertSettingsDto.Response.builder()
                .alertColor(isPro ? creator.getAlertColor() : "cyan")
                .alertAnimation(isPro ? creator.getAlertAnimation() : "slide")
                .alertSound(isPro ? creator.getAlertSound() : "chime")
                .alertDuration(isPro ? creator.getAlertDuration() : 8)
                .alertImageUrl(isPro ? creator.getAlertImageUrl() : null)
                .isPro(isPro)
                .build());
    }

    /**
     * Update alert settings (authenticated, Pro only).
     */
    @PutMapping("/settings")
    public ResponseEntity<AlertSettingsDto.Response> updateSettings(
            @AuthenticationPrincipal String username,
            @RequestBody AlertSettingsDto.UpdateRequest request) {

        Creator creator = creatorRepository.findByUsername(username)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Creator not found"));

        if (creator.getSubscriptionPlan() != SubscriptionPlan.PRO) {
            throw new ApiExceptions.ForbiddenException("Custom alerts are a Pro feature");
        }

        if (request.getAlertColor() != null) creator.setAlertColor(request.getAlertColor());
        if (request.getAlertAnimation() != null) creator.setAlertAnimation(request.getAlertAnimation());
        if (request.getAlertSound() != null) creator.setAlertSound(request.getAlertSound());
        if (request.getAlertDuration() != null) creator.setAlertDuration(request.getAlertDuration());
        // Always update image — null clears it back to default ⚡
        creator.setAlertImageUrl(request.getAlertImageUrl());

        creatorRepository.save(creator);

        return ResponseEntity.ok(AlertSettingsDto.Response.builder()
                .alertColor(creator.getAlertColor())
                .alertAnimation(creator.getAlertAnimation())
                .alertSound(creator.getAlertSound())
                .alertDuration(creator.getAlertDuration())
                .alertImageUrl(creator.getAlertImageUrl())
                .isPro(true)
                .build());
    }

    /**
     * Send a test alert for preview purposes.
     */
    @PostMapping("/{username}/test")
    public ResponseEntity<Void> testAlert(@PathVariable String username) {
        tipAlertService.broadcastTest(username);
        return ResponseEntity.ok().build();
    }
}
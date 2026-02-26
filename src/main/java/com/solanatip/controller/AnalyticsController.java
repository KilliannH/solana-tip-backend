package com.solanatip.controller;

import com.solanatip.dto.AnalyticsDto;
import com.solanatip.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<AnalyticsDto.Overview> getAnalytics(
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "30d") String period) {

        return ResponseEntity.ok(analyticsService.getAnalytics(username, period));
    }
}
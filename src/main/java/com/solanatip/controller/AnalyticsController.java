package com.solanatip.controller;

import com.solanatip.dto.AnalyticsDto;
import com.solanatip.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<AnalyticsDto.Overview> getAnalytics(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "30d") String period) {

        String username = userDetails.getUsername();
        return ResponseEntity.ok(analyticsService.getAnalytics(username, period));
    }
}

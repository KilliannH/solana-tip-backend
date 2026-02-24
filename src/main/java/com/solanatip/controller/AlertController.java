package com.solanatip.controller;

import com.solanatip.service.TipAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlertController {

    private final TipAlertService tipAlertService;

    /**
     * SSE endpoint for OBS overlay.
     */
    @GetMapping(value = "/{username}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String username) {
        return tipAlertService.subscribe(username);
    }

    /**
     * Send a test alert to preview the overlay.
     */
    @PostMapping("/{username}/test")
    public ResponseEntity<Void> testAlert(@PathVariable String username) {
        tipAlertService.broadcastTest(username);
        return ResponseEntity.ok().build();
    }
}
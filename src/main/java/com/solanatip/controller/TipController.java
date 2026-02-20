package com.solanatip.controller;

import com.solanatip.dto.TipDto;
import com.solanatip.service.TipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tips")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class TipController {

    private final TipService tipService;

    @PostMapping
    public ResponseEntity<TipDto.Response> createTip(@Valid @RequestBody TipDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tipService.createTip(request));
    }

    @GetMapping("/creator/{username}")
    public ResponseEntity<Page<TipDto.Response>> getTipsByCreator(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(tipService.getTipsByCreator(username, page, size));
    }

    @GetMapping("/sender/{walletAddress}")
    public ResponseEntity<Page<TipDto.Response>> getTipsBySender(
            @PathVariable String walletAddress,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(tipService.getTipsBySender(walletAddress, page, size));
    }
}

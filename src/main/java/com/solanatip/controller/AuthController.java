package com.solanatip.controller;

import com.solanatip.dto.AuthDto;
import com.solanatip.service.AuthService;
import com.solanatip.service.CreatorService;
import com.solanatip.service.EmailVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final CreatorService creatorService;

    // ========== Email/Password ==========

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // ========== Wallet Signature ==========

    /**
     * Step 1: Request a nonce for the wallet to sign.
     * Returns the nonce + a human-readable message to sign.
     * Also indicates if the wallet is already registered (login vs register flow).
     */
    @PostMapping("/wallet/nonce")
    public ResponseEntity<AuthDto.WalletNonceResponse> requestNonce(@Valid @RequestBody AuthDto.WalletNonceRequest request) {
        return ResponseEntity.ok(authService.generateNonce(request));
    }

    /**
     * Step 2a: Login with wallet signature (existing wallet).
     */
    @PostMapping("/wallet/login")
    public ResponseEntity<AuthDto.AuthResponse> walletLogin(@Valid @RequestBody AuthDto.WalletLoginRequest request) {
        return ResponseEntity.ok(authService.walletLogin(request));
    }

    /**
     * Step 2b: Register + login with wallet signature (new wallet).
     * The nonce must have been requested in Step 1.
     */
    @PostMapping("/wallet/register")
    public ResponseEntity<AuthDto.AuthResponse> walletRegister(
            @Valid @RequestBody AuthDto.WalletRegisterRequest request,
            @RequestHeader("X-Wallet-Nonce") String nonce) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.walletRegister(request, nonce));
    }

    // ========== Email Verification ==========

    @GetMapping("/verify-email")
    public ResponseEntity<java.util.Map<String, String>> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok(java.util.Map.of("message", "Email verified successfully!"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<java.util.Map<String, String>> resendVerification(
            @RequestBody java.util.Map<String, String> body) {
        emailVerificationService.resendVerification(body.get("username"));
        return ResponseEntity.ok(java.util.Map.of("message", "Verification email sent"));
    }

    // ========== Unsubscribe ==========

    @GetMapping("/unsubscribe")
    public ResponseEntity<java.util.Map<String, String>> unsubscribe(@RequestParam String u) {
        try {
            String username = new String(java.util.Base64.getUrlDecoder().decode(u), java.nio.charset.StandardCharsets.UTF_8);
            com.solanatip.dto.CreatorDto.SettingsRequest settings = new com.solanatip.dto.CreatorDto.SettingsRequest();
            settings.setNotifyTipReceived(false);
            settings.setNotifyMarketing(false);
            creatorService.updateSettings(username, settings);
            return ResponseEntity.ok(java.util.Map.of("message", "Successfully unsubscribed"));
        } catch (Exception e) {
            return ResponseEntity.ok(java.util.Map.of("message", "Successfully unsubscribed"));
        }
    }
}
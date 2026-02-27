package com.solanatip.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

public class AuthDto {

    // ========== Email/Password Registration ==========

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class RegisterRequest {

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, hyphens and underscores")
        private String username;

        @NotBlank(message = "Display name is required")
        @Size(max = 50)
        private String displayName;

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;

        @NotBlank(message = "Wallet address is required")
        private String walletAddress;

        @Size(max = 500)
        private String bio;

        private String avatarUrl;
    }

    // ========== Email/Password Login ==========

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class LoginRequest {

        @NotBlank(message = "Email is required")
        @Email
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    // ========== Wallet Signature Auth ==========

    /** Step 1: Request a nonce to sign */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class WalletNonceRequest {

        @NotBlank(message = "Wallet address is required")
        private String walletAddress;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class WalletNonceResponse {
        private String nonce;
        private String message;
        private boolean registered;
    }

    /** Step 2: Send signed nonce to authenticate */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class WalletLoginRequest {

        @NotBlank(message = "Wallet address is required")
        private String walletAddress;

        @NotBlank(message = "Signature is required")
        private String signature;
    }

    /** Step 2 (first time): Register + authenticate via wallet */
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class WalletRegisterRequest {

        @NotBlank(message = "Wallet address is required")
        private String walletAddress;

        @NotBlank(message = "Signature is required")
        private String signature;

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
        private String username;

        @NotBlank(message = "Display name is required")
        @Size(max = 50)
        private String displayName;

        @Size(max = 500)
        private String bio;

        private String avatarUrl;
    }

    // ========== Auth Response ==========

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class AuthResponse {
        private String token;
        private String refreshToken;
        private String type;
        private String username;
        private String walletAddress;
    }

    // ========== Refresh Token ==========

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class RefreshRequest {
        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
    }
}
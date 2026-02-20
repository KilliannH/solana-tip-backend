package com.solanatip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class CreatorDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Username can only contain letters, numbers, hyphens and underscores")
        private String username;

        @NotBlank(message = "Display name is required")
        @Size(max = 50)
        private String displayName;

        @Size(max = 500)
        private String bio;

        @NotBlank(message = "Wallet address is required")
        private String walletAddress;

        private String avatarUrl;
        private String bannerUrl;

        @Pattern(regexp = "^(cyan|purple|pink|green|blue)$", message = "Invalid theme color")
        private String themeColor;

        private String youtubeUrl;
        private String twitchUrl;
        private String tiktokUrl;
        private String twitterUrl;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @Size(max = 50)
        private String displayName;

        @Size(max = 500)
        private String bio;

        private String avatarUrl;
        private String bannerUrl;

        @Pattern(regexp = "^(cyan|purple|pink|green|blue)$", message = "Invalid theme color")
        private String themeColor;

        private String youtubeUrl;
        private String twitchUrl;
        private String tiktokUrl;
        private String twitterUrl;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {
        private UUID id;
        private String username;
        private String displayName;
        private String bio;
        private String walletAddress;
        private String email;
        private String avatarUrl;
        private String bannerUrl;
        private String themeColor;
        private String youtubeUrl;
        private String twitchUrl;
        private String tiktokUrl;
        private String twitterUrl;
        private boolean emailVerified;
        private BigDecimal totalTipsReceived;
        private long tipCount;
        private LocalDateTime createdAt;
    }
}